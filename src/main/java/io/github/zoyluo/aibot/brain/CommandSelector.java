package io.github.zoyluo.aibot.brain;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zoyluo.aibot.AIBotConfig;
import io.github.zoyluo.aibot.log.BotLog;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** StepFun chooses a supported command and its argument after Laya selects the command intent. */
public final class CommandSelector {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "aibot-command-selector");
        thread.setDaemon(true);
        return thread;
    });

    private static final String SYSTEM = """
            你负责从玩家原话中选择要执行的 Minecraft 管理指令。只允许以下 action 和 value：
            gamemode: survival, creative, adventure, spectator（设置玩家游戏模式）
            keep_inventory: on, off（设置死亡不掉落）
            weather: clear, rain, thunder（设置天气）
            time: day, noon, night, midnight（设置时间）
            give_blueprint: value 留空（把蓝图图纸物品给玩家；具体蓝图随后另选）
            none: value 留空（没有明确要求执行上述指令，或只是在讨论、询问）
            注意：'给我设置成生存模式' 是 gamemode/survival；'给我房子的图纸' 是 give_blueprint；
            '为我搭建一个日式建筑' 是实际建造，不是管理指令，应选 none。
            必须只输出一个 JSON 对象，如 {"action":"gamemode","value":"survival"}。不要解释。
            """;

    private CommandSelector() {
    }

    public record Selection(Action action, String value) {
    }

    public enum Action {
        GAMEMODE, KEEP_INVENTORY, WEATHER, TIME, GIVE_BLUEPRINT
    }

    public static CompletableFuture<Optional<Selection>> select(String message) {
        return CompletableFuture.supplyAsync(() -> {
            AIBotConfig.DeepSeek config = AIBotConfig.get().deepseek();
            try {
                String content = new DeepSeekApiClient(config).chat(
                        List.of(ChatMessage.system(SYSTEM), ChatMessage.user(message)), List.of()).content();
                return parseSelection(content);
            } catch (DeepSeekApiException | RuntimeException exception) {
                BotLog.warn(io.github.zoyluo.aibot.log.LogCategory.API, null,
                        "command_selection_failed", "reason", exception.getMessage());
                return Optional.empty();
            }
        }, EXECUTOR);
    }

    static Optional<Selection> parseSelection(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = content.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7).trim();
            } else if (json.startsWith("```")) {
                json = json.substring(3).trim();
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3).trim();
            }
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String action = object.get("action").getAsString();
            String value = object.has("value") && !object.get("value").isJsonNull()
                    ? object.get("value").getAsString() : "";
            return switch (action) {
                case "gamemode" -> oneOf(Action.GAMEMODE, value,
                        "survival", "creative", "adventure", "spectator");
                case "keep_inventory" -> oneOf(Action.KEEP_INVENTORY, value, "on", "off");
                case "weather" -> oneOf(Action.WEATHER, value, "clear", "rain", "thunder");
                case "time" -> oneOf(Action.TIME, value, "day", "noon", "night", "midnight");
                case "give_blueprint" -> value.isBlank()
                        ? Optional.of(new Selection(Action.GIVE_BLUEPRINT, "")) : Optional.empty();
                default -> Optional.empty();
            };
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Selection> oneOf(Action action, String value, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return Optional.of(new Selection(action, value));
            }
        }
        return Optional.empty();
    }
}
