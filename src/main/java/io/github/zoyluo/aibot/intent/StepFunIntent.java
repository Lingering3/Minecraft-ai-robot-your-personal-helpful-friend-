package io.github.zoyluo.aibot.intent;

public record StepFunIntent(
        String intent,
        String actionSummary,
        double confidence
) {
    public boolean isBuild() {
        return "build".equals(intent);
    }

    public boolean isChat() {
        return "chat".equals(intent);
    }

    public String promptLine() {
        return "StepFun task recognition: intent=" + intent
                + ", confidence=" + confidence
                + ", action=" + actionSummary
                + ". intent values: chat / build / mine / gather / craft / farm / fish / trade / fight / follow / sleep / stockpile / status / unknown."
                + " chat/status are conversational and must not interrupt active long-running work; task intents may use high-level tools.";
    }
}
