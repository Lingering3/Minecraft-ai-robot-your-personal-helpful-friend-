package io.github.zoyluo.aibot.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zoyluo.aibot.AIBotConfig;
import io.github.zoyluo.aibot.log.BotLog;
import io.github.zoyluo.aibot.log.LogCategory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Remote Litematic library.
 *
 * <p>The manifest is intentionally tiny and is safe to fetch during recommendation. The heavy
 * .litematic payload is downloaded only after StepFun has selected a concrete blueprint id.</p>
 */
public final class RemoteBlueprints {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static volatile Cache cache = new Cache("", 0L, List.of());

    private RemoteBlueprints() {
    }

    public static List<RemoteEntry> entries() {
        AIBotConfig.RemoteBlueprints config = AIBotConfig.get().remoteBlueprints();
        if (config == null || !config.isEnabled() || config.manifestUrl() == null
                || config.manifestUrl().isBlank()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        Cache snapshot = cache;
        long ttlMs = Math.max(1, config.cacheSeconds()) * 1000L;
        if (config.manifestUrl().equals(snapshot.manifestUrl()) && now - snapshot.loadedAtMs() < ttlMs) {
            return snapshot.entries();
        }
        synchronized (RemoteBlueprints.class) {
            snapshot = cache;
            if (config.manifestUrl().equals(snapshot.manifestUrl()) && now - snapshot.loadedAtMs() < ttlMs) {
                return snapshot.entries();
            }
            List<RemoteEntry> fetched = fetchManifest(config);
            cache = new Cache(config.manifestUrl(), System.currentTimeMillis(), fetched);
            return fetched;
        }
    }

    public static Optional<RemoteEntry> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return entries().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst();
    }

    public static boolean has(String id) {
        return find(id).isPresent();
    }

    public static String sizeText(String id) {
        return find(id)
                .map(RemoteEntry::sizeText)
                .filter(text -> !text.isBlank())
                .orElse("?");
    }

    public static boolean downloadIfNeeded(String id) throws IOException {
        if (LitematicaImporter.existsLocal(id)) {
            return true;
        }
        RemoteEntry entry = find(id).orElse(null);
        if (entry == null) {
            return false;
        }
        download(entry);
        return true;
    }

    public static void clearCache() {
        cache = new Cache("", 0L, List.of());
    }

    private static List<RemoteEntry> fetchManifest(AIBotConfig.RemoteBlueprints config) {
        try {
            URI manifestUri = safeUri(config.manifestUrl());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(manifestUri)
                    .timeout(Duration.ofSeconds(Math.max(1, config.downloadTimeoutSeconds())))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                BotLog.warn(LogCategory.CONFIG, null, "remote_blueprint_manifest_failed",
                        "status", response.statusCode());
                return List.of();
            }
            return parseManifest(manifestUri, response.body());
        } catch (IOException exception) {
            BotLog.warn(LogCategory.CONFIG, null, "remote_blueprint_manifest_io_error",
                    "message", exception.getMessage());
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            BotLog.warn(LogCategory.CONFIG, null, "remote_blueprint_manifest_interrupted",
                    "message", exception.getMessage());
            return List.of();
        } catch (RuntimeException exception) {
            BotLog.warn(LogCategory.CONFIG, null, "remote_blueprint_manifest_bad",
                    "message", exception.getMessage());
            return List.of();
        }
    }

    private static List<RemoteEntry> parseManifest(URI manifestUri, String body) {
        JsonElement root = JsonParser.parseString(body);
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().has("blueprints")
                && root.getAsJsonObject().get("blueprints").isJsonArray()) {
            array = root.getAsJsonObject().getAsJsonArray("blueprints");
        } else {
            return List.of();
        }
        List<RemoteEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String file = string(object, "file", "");
            String id = string(object, "id", stripExtension(file));
            if (!isSafeId(id)) {
                continue;
            }
            String rawUrl = firstPresent(object, "url", "downloadUrl", "href");
            if (rawUrl.isBlank()) {
                rawUrl = file.isBlank() ? id + ".litematic" : file;
            }
            URI downloadUri = resolve(manifestUri, rawUrl);
            String name = firstPresent(object, "name", "displayName", "title");
            if (name.isBlank()) {
                name = id;
            }
            List<String> tags = tags(object);
            String size = size(object);
            long bytes = longValue(object, "bytes", longValue(object, "sizeBytes", -1L));
            entries.add(new RemoteEntry(id, name, tags, downloadUri, size, bytes));
        }
        return List.copyOf(entries);
    }

    private static void download(RemoteEntry entry) throws IOException {
        AIBotConfig.RemoteBlueprints config = AIBotConfig.get().remoteBlueprints();
        long maxBytes = (long) Math.max(1, config.maxFileSizeMb()) * 1024L * 1024L;
        if (entry.bytes() > maxBytes) {
            throw new IOException("remote_litematic_too_large: " + entry.id());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(entry.url())
                    .timeout(Duration.ofSeconds(Math.max(1, config.downloadTimeoutSeconds())))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("remote_litematic_http_" + response.statusCode() + ": " + entry.id());
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new IOException("remote_litematic_empty: " + entry.id());
            }
            if (body.length > maxBytes) {
                throw new IOException("remote_litematic_too_large: " + entry.id());
            }
            Path dir = LitematicaImporter.structuresDir();
            Files.createDirectories(dir);
            Path target = safeLocalPath(entry.id());
            Path temp = Files.createTempFile(dir, entry.id().replace(' ', '_'), ".download");
            try {
                Files.write(temp, body);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            BotLog.config("remote_blueprint_downloaded",
                    "id", entry.id(),
                    "bytes", body.length,
                    "target", target);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("remote_litematic_interrupted: " + entry.id(), exception);
        }
    }

    private static Path safeLocalPath(String id) throws IOException {
        if (!isSafeId(id)) {
            throw new IOException("remote_litematic_bad_id: " + id);
        }
        Path dir = LitematicaImporter.structuresDir();
        Path path = dir.resolve(id + ".litematic").normalize();
        if (!path.startsWith(dir.normalize())) {
            throw new IOException("remote_litematic_bad_path: " + id);
        }
        return path;
    }

    private static boolean isSafeId(String id) {
        return id != null && !id.isBlank()
                && id.indexOf('/') < 0
                && id.indexOf('\\') < 0
                && !id.contains("..")
                && !id.endsWith(".")
                && !id.endsWith(" ");
    }

    private static URI resolve(URI manifestUri, String value) {
        URI raw = safeUri(value);
        return raw.isAbsolute() ? raw : manifestUri.resolve(raw);
    }

    private static URI safeUri(String value) {
        String normalized = value == null ? "" : value.trim().replace(" ", "%20");
        try {
            return URI.create(normalized);
        } catch (IllegalArgumentException ignored) {
            return URI.create(encodePath(normalized));
        }
    }

    private static String encodePath(String value) {
        String[] parts = value.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (i == 0 && parts[i].contains(":")) {
                continue;
            }
            parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return String.join("/", parts);
    }

    private static String string(JsonObject object, String name, String defaultValue) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }

    private static String firstPresent(JsonObject object, String... names) {
        for (String name : names) {
            String value = string(object, name, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static long longValue(JsonObject object, String name, long defaultValue) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsLong() : defaultValue;
    }

    private static List<String> tags(JsonObject object) {
        JsonElement element = object.get("tags");
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonElement tag : element.getAsJsonArray()) {
            if (tag.isJsonPrimitive()) {
                tags.add(tag.getAsString());
            }
        }
        return List.copyOf(tags);
    }

    private static String size(JsonObject object) {
        String text = firstPresent(object, "size", "dimensions");
        if (!text.isBlank()) {
            return text;
        }
        int width = intValue(object, "width");
        int height = intValue(object, "height");
        int depth = intValue(object, "depth");
        if (width > 0 && height > 0 && depth > 0) {
            return width + "x" + height + "x" + depth;
        }
        return "";
    }

    private static int intValue(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    private static String stripExtension(String file) {
        if (file == null || file.isBlank()) {
            return "";
        }
        String name = Path.of(file).getFileName().toString();
        return name.replaceFirst("\\.litematic$", "");
    }

    private record Cache(String manifestUrl, long loadedAtMs, List<RemoteEntry> entries) {
    }

    public record RemoteEntry(String id, String name, List<String> tags, URI url, String sizeText, long bytes) {
        public RemoteEntry {
            tags = tags == null ? List.of() : List.copyOf(tags);
            sizeText = sizeText == null ? "" : sizeText;
        }
    }
}
