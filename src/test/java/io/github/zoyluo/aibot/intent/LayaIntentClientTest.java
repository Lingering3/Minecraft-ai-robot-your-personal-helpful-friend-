package io.github.zoyluo.aibot.intent;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LayaIntentClientTest {
    @Test
    void buildIsRecognized() {
        assertEquals("build", LayaIntentClient.parseAnswers(
                answers("build", 0.9, "yes", 0.8)).orElseThrow().intent());
    }

    @Test
    void commandAnswerNoLongerRescuesWeakPrimaryAnswer() {
        assertTrue(LayaIntentClient.parseAnswers(
                answers("chat", 0.3, "yes", 0.8)).isEmpty());
    }

    @Test
    void discussionStaysChatWhenCommandAnswerIsNo() {
        assertEquals("chat", LayaIntentClient.parseAnswers(
                answers("chat", 0.8, "no", 0.8)).orElseThrow().intent());
    }

    @Test
    void unknownDoesNotTriggerWork() {
        assertTrue(LayaIntentClient.parseAnswers(
                answers("unknown", 0.9, "no", 0.8)).isEmpty());
    }

    private static JsonObject answers(String primary, double primaryConfidence,
                                      String command, double commandConfidence) {
        JsonObject answers = new JsonObject();
        answers.add("intent", choice(primary, primaryConfidence));
        answers.add("command_intent", choice(command, commandConfidence));
        return answers;
    }

    private static JsonObject choice(String name, double confidence) {
        JsonObject choice = new JsonObject();
        choice.addProperty("choice", name);
        choice.addProperty("confidence", confidence);
        choice.addProperty("answer_confidence", confidence);
        return choice;
    }
}
