package io.github.zoyluo.aibot.blueprint;

import io.github.zoyluo.aibot.AIBotConfig;
import io.github.zoyluo.aibot.brain.ChatMessage;
import io.github.zoyluo.aibot.brain.DeepSeekApiClient;
import io.github.zoyluo.aibot.brain.DeepSeekApiException;
import io.github.zoyluo.aibot.log.BotLog;
import io.github.zoyluo.aibot.task.BlueprintLoader;
import io.github.zoyluo.aibot.task.BlueprintSchema;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 建造任务确认后,用 StepFun(DeepSeek API)从本地蓝图库中选出最符合玩家需求的那一个蓝图。
 *
 * 候选 = BlueprintCatalog.entries()(内置 json 蓝图 + blueprints/structures/* 结构蓝图);
 * 模型根据玩家原话(可能带风格/规模/用途等网上知识)打分选 id,只输出 id。
 * 返回的 Optional 为空表示模型无法确定;调用方不会做本地打分或默认兜底。
 */
public final class BlueprintRecommender {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "aibot-blueprint-recommender");
        thread.setDaemon(true);
        return thread;
    });

    private BlueprintRecommender() {
    }

    public record Recommendation(String blueprintId, String buildKind) {
        public Recommendation {
            blueprintId = blueprintId == null ? "" : blueprintId;
            buildKind = buildKind == null || buildKind.isBlank() ? "unknown" : buildKind;
        }
    }

    public static CompletableFuture<Optional<Recommendation>> recommend(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return recommendSync(query);
            } catch (RuntimeException exception) {
                BotLog.error(null, "blueprint_recommend_failed", exception, "message",
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                return Optional.empty();
            }
        }, EXECUTOR);
    }

    private static Optional<Recommendation> recommendSync(String query) {
        AIBotConfig.DeepSeek config = AIBotConfig.get().deepseek();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            return Optional.empty();
        }
        List<BlueprintCatalog.BlueprintEntry> candidates = BlueprintCatalog.entries().stream()
                .sorted(Comparator.comparing(BlueprintCatalog.BlueprintEntry::id))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            BlueprintCatalog.BlueprintEntry entry = candidates.get(i);
            catalog.append(i).append(". ").append(entry.id())
                    .append(" | name=").append(entry.name())
                    .append(" | 中文名=").append(BlueprintCatalog.chineseName(entry))
                    .append(" | tags=").append(String.join(",", entry.tags()))
                    .append(" | size=").append(sizeOf(entry.id()))
                    .append("\n");
        }
        String system = """
                你是 Minecraft 建筑蓝图推荐官。下面给出本地蓝图库中每个候选的 id、中文名、关键词和尺寸。
                请结合玩家完整对话消息与建筑常识,先判断建筑类型 build_kind,再从候选清单中选出最符合该需求的一个蓝图。
                只能从候选清单中选择,禁止发明不存在的 id。id 可能包含空格,必须原样输出。
                build_kind 必须用一个简短英文类别,例如 house/casino/guild_house/japanese/temple/tower/bridge/castle/farm/other。
                极其重要:你的最终回复只能是两行,格式严格为:
                建筑类型: <build_kind>
                最终选择: <id>
                禁止输出表格、解释、列表、推理过程、Markdown 或任何其他文字,只输出这两行。""";
        String user = "玩家需求: " + (query == null ? "" : query)
                + "\n\n候选蓝图清单:\n" + catalog
                + "\n\n记住:只输出两行「建筑类型: <build_kind>」和「最终选择: <id>」。";
        try {
            DeepSeekApiClient client = new DeepSeekApiClient(config);
            var response = client.chat(
                    List.of(ChatMessage.system(system), ChatMessage.user(user)),
                    List.of());
            String content = response.content();
            if (content == null || content.isBlank()) {
                return Optional.empty();
            }
            return pickCandidate(content, candidates);
        } catch (DeepSeekApiException exception) {
            BotLog.warn(io.github.zoyluo.aibot.log.LogCategory.API, null,
                    "blueprint_recommend_api_error", "reason",
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<Recommendation> pickCandidate(String content, List<BlueprintCatalog.BlueprintEntry> candidates) {
        String trimmed = content.trim();
        String buildKind = "unknown";
        java.util.regex.Matcher kindMarker = java.util.regex.Pattern
                .compile("(?im)^(?:建筑类型|BUILD_KIND|KIND)\\s*[:：]\\s*(.+?)\\s*$")
                .matcher(trimmed);
        if (kindMarker.find()) {
            buildKind = normalizeKind(kindMarker.group(1));
        }
        // 优先: 「最终选择: <id>」「SELECTED: <id>」标记后的 id
        java.util.regex.Matcher marker = java.util.regex.Pattern
                .compile("(?im)^(?:最终选择|SELECTED|SELECTION)\\s*[:：]?\\s*(.+?)\\s*$")
                .matcher(trimmed);
        if (marker.find()) {
            String marked = unquote(marker.group(1).trim());
            for (BlueprintCatalog.BlueprintEntry entry : candidates) {
                if (entry.id().equals(marked)) {
                    return Optional.of(new Recommendation(entry.id(), buildKind));
                }
            }
        }
        // 纯 id 输出
        for (BlueprintCatalog.BlueprintEntry entry : candidates) {
            String id = entry.id();
            if (trimmed.equals(id)
                    || trimmed.equals("\"" + id + "\"")
                    || trimmed.contains("\"" + id + "\"")
                    || trimmed.contains("blueprint\":" + id)
                    || trimmed.contains("blueprint\":\"" + id + "\"")) {
                return Optional.of(new Recommendation(id, buildKind));
            }
        }
        // 单候选 id 出现在文本中(模型输出了表格/解释时):
        // 统计每个 id 出现次数,若只有一个 id 出现,选它;否则放弃(交给本地回退,避免误选)。
        String found = null;
        int distinctFound = 0;
        for (BlueprintCatalog.BlueprintEntry entry : candidates) {
            String id = entry.id();
            int idx = trimmed.indexOf(id);
            if (idx >= 0) {
                boolean leftOk = idx == 0 || !Character.isLetterOrDigit(trimmed.charAt(idx - 1));
                int end = idx + id.length();
                boolean rightOk = end >= trimmed.length() || !Character.isLetterOrDigit(trimmed.charAt(end));
                if (leftOk && rightOk) {
                    distinctFound++;
                    found = id;
                }
            }
        }
        if (distinctFound == 1) {
            return Optional.of(new Recommendation(found, buildKind));
        }
        return Optional.empty();
    }

    private static String normalizeKind(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String normalized = unquote(raw).trim().toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static String sizeOf(String id) {
        try {
            BlueprintSchema schema = BlueprintLoader.load(id);
            return schema.width() + "x" + schema.height() + "x" + schema.depth();
        } catch (IOException ignored) {
            return "?";
        }
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
