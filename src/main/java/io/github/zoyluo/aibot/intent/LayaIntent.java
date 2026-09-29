package io.github.zoyluo.aibot.intent;

public record LayaIntent(
        String intent,
        String buildKind,
        double confidence,
        double answerConfidence
) {
    public boolean isBuild() {
        return "build".equals(intent);
    }

    public boolean isChat() {
        return "chat".equals(intent);
    }

    public String promptLine() {
        return "Laya task recognition: intent=" + intent
                + ", confidence=" + confidence
                + ", answer_confidence=" + answerConfidence
                + ". intent 取值: chat(普通对话) / build(实际建造) / mine(挖矿) / gather(搜集) / craft(合成制作) / farm(农业畜牧) / fish(钓鱼) / trade(交易) / fight(对战守卫) / follow(跟随移动) / sleep(休息照明) / stockpile(囤积补给)。"
                + " 普通对话(intent=chat)用 say 直接回答即可;任务型对话选择对应的高层任务工具落地。"
                + " 建造请求由独立蓝图流程交给 StepFun 选择建筑类型和蓝图。";
    }
}
