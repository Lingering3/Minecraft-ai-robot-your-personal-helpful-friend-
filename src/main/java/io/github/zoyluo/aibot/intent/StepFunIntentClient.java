package io.github.zoyluo.aibot.intent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zoyluo.aibot.AIBotConfig;
import io.github.zoyluo.aibot.brain.ChatMessage;
import io.github.zoyluo.aibot.brain.ChatResponse;
import io.github.zoyluo.aibot.brain.DeepSeekApiClient;
import io.github.zoyluo.aibot.log.BotLog;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class StepFunIntentClient {
    public static final StepFunIntentClient INSTANCE = new StepFunIntentClient();

    private static final Set<String> ALLOWED = Set.of(
            "chat", "build", "mine", "gather", "craft", "farm", "fish", "trade",
            "fight", "follow", "sleep", "stockpile", "status", "unknown");
    private static final double MIN_CONFIDENCE = 0.35D;

    private StepFunIntentClient() {
    }

    public CompletableFuture<Optional<StepFunIntent>> classify(String text) {
        AIBotConfig.DeepSeek config = AIBotConfig.get().deepseek();
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatResponse response = new DeepSeekApiClient(config).chat(List.of(
                        ChatMessage.system(systemPrompt()),
                        ChatMessage.user(text == null ? "" : text)
                ), List.of());
                return parse(response.content());
            } catch (Exception exception) {
                BotLog.commSystem("stepfun_intent_failed", "message", message(exception));
                return Optional.<StepFunIntent>empty();
            }
        });
    }

    static Optional<StepFunIntent> parse(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(extractJson(content)).getAsJsonObject();
            String intent = string(root, "intent").toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(intent) || "unknown".equals(intent)) {
                return Optional.empty();
            }
            double confidence = root.has("confidence") && root.get("confidence").isJsonPrimitive()
                    ? root.get("confidence").getAsDouble() : 0.0D;
            if (confidence < MIN_CONFIDENCE) {
                return Optional.empty();
            }
            return Optional.of(new StepFunIntent(intent, string(root, "action"), confidence));
        } catch (RuntimeException exception) {
            BotLog.commSystem("stepfun_intent_parse_failed", "message", message(exception), "content", content);
            return Optional.empty();
        }
    }

    private static String systemPrompt() {
        return """
                你是 Minecraft AI bot 的第一层分类器。只输出 JSON,不要解释。
                JSON 格式: {"intent":"chat|build|mine|gather|craft|farm|fish|trade|fight|follow|sleep|stockpile|status|unknown","confidence":0.0到1.0,"action":"一句中文说明 bot 将做什么"}

                分类规则:
                chat: 普通闲聊、询问能力、讨论建筑、问有什么蓝图、表达想法,没有要求 bot 立刻改变世界。
                build: 明确要求 bot 实际搭建/建造/盖一个成品建筑。只谈建筑、问会不会建、索要图纸都不是 build。
                status: 玩家问 bot 当前在干什么、进度如何、状态如何。
                follow: 过来、跟随、待命、走到某处。
                mine/gather/craft/farm/fish/trade/fight/sleep/stockpile: 对应 Minecraft 世界内实际行动。
                unknown: 无法判断。
                暂时没有 command/指令 分类；设置模式、天气、时间、死亡不掉落、给蓝图等管理指令请求都归为 chat。
                """;
    }

    private static String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('{');
            int last = trimmed.lastIndexOf('}');
            if (first >= 0 && last > first) {
                return trimmed.substring(first, last + 1);
            }
        }
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        return first >= 0 && last > first ? trimmed.substring(first, last + 1) : trimmed;
    }

    private static String string(JsonObject root, String name) {
        return root.has(name) && root.get(name).isJsonPrimitive() ? root.get(name).getAsString() : "";
    }

    private static String message(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
