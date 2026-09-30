package io.github.zoyluo.aibot.blueprint;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlueprintCatalog {
    private static final Gson GSON = new Gson();
    private static final BlueprintEntry SMALL_HUT = new BlueprintEntry(
            "small_hut",
            "Small Hut",
            List.of("house", "hut", "shelter", "base", "wood", "small", "房子", "小屋", "家", "庇护所"));
    private static final Map<String, String> NAME_TRANSLATIONS = nameTranslations();

    private BlueprintCatalog() {
    }

    public static List<BlueprintEntry> entries() {
        List<BlueprintEntry> entries = new ArrayList<>();
        entries.add(SMALL_HUT);
        entries.add(new BlueprintEntry(
                "hut_5x5",
                "5x5 Hut",
                List.of("house", "hut", "shelter", "base", "stone", "small", "房子", "小屋", "石头")));
        entries.add(new BlueprintEntry(
                "glass_cabin",
                "Glass Cabin",
                List.of("house", "cabin", "glass", "modern", "base", "房子", "玻璃", "现代", "小屋")));
        entries.add(new BlueprintEntry(
                "watch_tower",
                "Watch Tower",
                List.of("tower", "watchtower", "guard", "哨塔", "塔", "高塔", "瞭望塔")));
        entries.add(new BlueprintEntry(
                "simple_bridge",
                "Simple Bridge",
                List.of("bridge", "river", "crossing", "桥", "桥梁", "过河")));
        entries.addAll(indexEntries());
        entries.addAll(jsonBlueprintEntries());
        entries.addAll(structureEntries());
        entries.addAll(remoteEntries());
        return entries.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BlueprintEntry::id,
                        entry -> entry,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    private static List<BlueprintEntry> indexEntries() {
        Path index = blueprintDir().resolve("index.json");
        if (!Files.exists(index)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(index)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                return List.of();
            }
            List<BlueprintEntry> entries = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String id = string(object, "id", "");
                if (id.isBlank()) {
                    id = string(object, "file", "").replaceFirst("\\.[^.]+$", "");
                }
                if (id.isBlank()) {
                    continue;
                }
                String name = string(object, "name", id);
                List<String> tags = new ArrayList<>();
                if (object.has("tags") && object.get("tags").isJsonArray()) {
                    for (JsonElement tag : object.getAsJsonArray("tags")) {
                        if (tag.isJsonPrimitive()) {
                            tags.add(tag.getAsString());
                        }
                    }
                }
                entries.add(new BlueprintEntry(id, name, tags));
            }
            return entries;
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<BlueprintEntry> jsonBlueprintEntries() {
        Path dir = blueprintDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !"index.json".equals(path.getFileName().toString()))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .map(id -> new BlueprintEntry(id, id, List.of(id)))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static List<BlueprintEntry> structureEntries() {
        List<BlueprintEntry> entries = new ArrayList<>();
        for (String id : StructureImporter.listStructures()) {
            entries.add(new BlueprintEntry(id, id, List.of(id)));
        }
        // 内置 .litematic:使用中文显示名 + 语义关键词
        for (String id : LitematicaImporter.listLitematics()) {
            BuiltinBlueprints.Metadata metadata = BuiltinBlueprints.METADATA.get(id);
            if (metadata != null) {
                entries.add(new BlueprintEntry(id, metadata.displayName(), metadata.tags()));
            } else {
                entries.add(new BlueprintEntry(id, id, List.of(id)));
            }
        }
        return entries;
    }

    private static List<BlueprintEntry> remoteEntries() {
        List<BlueprintEntry> entries = new ArrayList<>();
        for (RemoteBlueprints.RemoteEntry remote : RemoteBlueprints.entries()) {
            List<String> tags = new ArrayList<>(remote.tags());
            tags.add(remote.id());
            tags.add(remote.name());
            entries.add(new BlueprintEntry(remote.id(), remote.name(), tags));
        }
        return entries;
    }

    private static String string(JsonObject object, String name, String defaultValue) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }

    public static String chineseName(BlueprintEntry entry) {
        if (entry == null) {
            return "未命名蓝图";
        }
        if (containsChinese(entry.name())) {
            return entry.name();
        }
        String normalized = entry.name()
                .replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (NAME_TRANSLATIONS.containsKey(lower)) {
            return NAME_TRANSLATIONS.get(lower);
        }
        List<String> translated = new ArrayList<>();
        for (String token : lower.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            translated.add(NAME_TRANSLATIONS.getOrDefault(token, token));
        }
        String joined = String.join("", translated).trim();
        return joined.isBlank() ? entry.id() : joined;
    }

    public static String chineseName(String id) {
        return entries().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .map(BlueprintCatalog::chineseName)
                .orElseGet(() -> chineseName(new BlueprintEntry(id, id, List.of(id))));
    }

    private static boolean containsChinese(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> nameTranslations() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("small hut", "小木屋");
        map.put("5x5 hut", "5x5小屋");
        map.put("glass cabin", "玻璃小屋");
        map.put("watch tower", "瞭望塔");
        map.put("simple bridge", "简易桥");
        map.put("casa facil", "简易住宅");
        map.put("casino trouville athosdream", "特鲁维尔赌场");
        map.put("custon cherry tree", "樱花树");
        map.put("custom cherry tree", "樱花树");
        map.put("end hub", "末地枢纽");
        map.put("endhub", "末地枢纽");
        map.put("guild house", "公会馆");
        map.put("guild house without interior", "公会馆（无内饰）");
        map.put("gunpowder farm", "火药农场");
        map.put("iron farm timsar", "刷铁机");
        map.put("japon japon mato", "日式庭院建筑");
        map.put("portal sword alllfy", "传送门剑雕塑");
        map.put("portal sword", "传送门剑雕塑");
        map.put("tree farm", "树场");
        map.put("trade hall alllfy", "村民交易大厅");
        map.put("tradehall alllfy", "村民交易大厅");
        map.put("trade hall", "村民交易大厅");
        map.put("tradehall", "村民交易大厅");
        map.put("casa", "住宅");
        map.put("facil", "简易");
        map.put("casino", "赌场");
        map.put("trouville", "特鲁维尔");
        map.put("athosdream", "");
        map.put("custom", "自定义");
        map.put("custon", "自定义");
        map.put("cherry", "樱花");
        map.put("end", "末地");
        map.put("hub", "枢纽");
        map.put("portal", "传送门");
        map.put("sword", "剑");
        map.put("alllfy", "");
        map.put("gunpowder", "火药");
        map.put("iron", "铁");
        map.put("timsar", "");
        map.put("trade", "交易");
        map.put("tree", "树");
        map.put("hall", "大厅");
        map.put("guild", "公会");
        map.put("house", "馆");
        map.put("without", "无");
        map.put("interior", "内饰");
        map.put("japon", "日式");
        map.put("mato", "");
        map.put("hut", "小屋");
        map.put("glass", "玻璃");
        map.put("cabin", "小屋");
        map.put("watch", "瞭望");
        map.put("tower", "塔");
        map.put("simple", "简易");
        map.put("bridge", "桥");
        map.put("castle", "城堡");
        map.put("villa", "别墅");
        map.put("modern", "现代");
        map.put("medieval", "中世纪");
        map.put("cottage", "乡间小屋");
        map.put("barn", "谷仓");
        map.put("fountain", "喷泉");
        map.put("lighthouse", "灯塔");
        map.put("market", "市场");
        map.put("stall", "摊位");
        map.put("stone", "石制");
        map.put("treehouse", "树屋");
        map.put("windmill", "风车");
        map.put("japanese", "日式");
        return Map.copyOf(map);
    }

    private static Path blueprintDir() {
        return FabricLoader.getInstance().getGameDir().resolve("blueprints");
    }

    public record BlueprintEntry(String id, String name, List<String> tags) {
        public BlueprintEntry {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
