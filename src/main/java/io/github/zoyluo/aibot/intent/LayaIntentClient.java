package io.github.zoyluo.aibot.intent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zoyluo.aibot.log.BotLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LayaIntentClient {
    public static final LayaIntentClient INSTANCE = new LayaIntentClient();

    private static final URI PREDICT_URI = URI.create("http://106.13.186.155:9072/predict");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final double MIN_CONFIDENCE = 0.45D;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private LayaIntentClient() {
    }

    public CompletableFuture<Optional<LayaIntent>> classify(String text) {
        HttpRequest request = HttpRequest.newBuilder(PREDICT_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(text)))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parse)
                .exceptionally(throwable -> {
                    BotLog.commSystem("laya_intent_failed", "message", message(throwable));
                    return Optional.empty();
                });
    }

    private Optional<LayaIntent> parse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            BotLog.commSystem("laya_intent_bad_status", "status", response.statusCode());
            return Optional.empty();
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject answers = object(root, "answers").orElse(null);
        if (answers == null) {
            return Optional.empty();
        }
        return parseAnswers(answers);
    }

    static Optional<LayaIntent> parseAnswers(JsonObject answers) {
        Choice intentChoice = choice(answers, "intent").orElse(null);
        String primary = intentChoice == null ? "" : intentChoice.choice().toLowerCase(Locale.ROOT);
        if ("build".equals(primary) && intentChoice.answerConfidence() >= MIN_CONFIDENCE) {
            return Optional.of(new LayaIntent("build", "unknown",
                    intentChoice.confidence(), intentChoice.answerConfidence()));
        }
        if (intentChoice == null || intentChoice.answerConfidence() < MIN_CONFIDENCE) {
            return Optional.empty();
        }
        String intent = primary;
        if ("unknown".equals(intent) || "other".equals(intent) || "command".equals(intent)) {
            return Optional.empty();
        }
        return Optional.of(new LayaIntent(
                intent,
                "unknown",
                intentChoice.confidence(),
                intentChoice.answerConfidence()));
    }

    private static Optional<Choice> choice(JsonObject answers, String name) {
        JsonObject node = object(answers, name).orElse(null);
        if (node == null || !node.has("choice")) {
            return Optional.empty();
        }
        return Optional.of(new Choice(
                node.get("choice").getAsString(),
                doubleOrZero(node, "confidence"),
                doubleOrZero(node, "answer_confidence")));
    }

    private static Optional<JsonObject> object(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject()
                ? Optional.of(element.getAsJsonObject())
                : Optional.empty();
    }

    private static double doubleOrZero(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsDouble() : 0.0D;
    }

    private static String payload(String text) {
        JsonObject root = new JsonObject();
        root.addProperty("text", text == null ? "" : text);
        root.addProperty("model", "multilingual");

        JsonObject questions = new JsonObject();
        JsonObject intent = new JsonObject();
        intent.addProperty("type", "choice");
        intent.addProperty("instructions", "判断玩家整句话现在要你做什么，而非只看有没有提到某个词。问能否建造、讨论建筑、询问蓝图或评价已建建筑是 chat；明确要求实际搭建才是 build。不要输出 command；设置模式、天气、时间、死亡不掉落、给图纸等请求暂时都当作 chat。其余行动按实际目标分类。");
        JsonObject intentCriteria = new JsonObject();
        intentCriteria.addProperty("chat", "只要求回答或讨论，不要求立刻行动；包括谈论建筑、问你会不会建、问有什么蓝图、询问任务状态和闲聊");
        intentCriteria.addProperty("build", "明确要求 AI 实际建造或搭建成品，例如为我搭建日式建筑、盖房、修桥、建塔；仅提到建筑、询问建筑或索要蓝图图纸不算 build");
        intentCriteria.addProperty("mine", "挖矿、寻找矿石、采集石头或矿物、挖隧道、垂直矿洞");
        intentCriteria.addProperty("gather", "收集、砍树、采集、收割野外资源、拾取掉落物、囤积物资");
        intentCriteria.addProperty("craft", "制作物品、工具、装备、合成或熔炼、放置工作台熔炉");
        intentCriteria.addProperty("farm", "种田、种植、收获农作物、养动物、挤奶、繁殖动物、灌溉农田");
        intentCriteria.addProperty("fish", "钓鱼、钓鱼竿、捕捉水生生物");
        intentCriteria.addProperty("trade", "与村民交易、以物易物、找村民");
        intentCriteria.addProperty("fight", "战斗、打怪、攻击、守卫、防御、保护、狩猎、对抗怪物或敌对生物");
        intentCriteria.addProperty("follow", "跟随玩家、过来、待命、原地等待、移动到某处、陪我走走");
        intentCriteria.addProperty("sleep", "睡觉、休息、过夜、起床、点火把照明");
        intentCriteria.addProperty("stockpile", "整理仓库、囤积物资、往箱子放东西、取物资、补给");
        intentCriteria.addProperty("unknown", "无法判断或不属于以上任何类别");
        intent.add("criteria", intentCriteria);
        questions.add("intent", intent);

        root.add("questions", questions);
        return root.toString();
    }

    private static String message(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof IOException) {
            return cause.getClass().getSimpleName();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private record Choice(String choice, double confidence, double answerConfidence) {
    }
}
