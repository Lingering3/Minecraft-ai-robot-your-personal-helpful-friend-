package io.github.zoyluo.aibot.brain;

import io.github.zoyluo.aibot.entity.AIPlayerEntity;
import net.minecraft.text.Text;

public final class BotSpeaker {
    private BotSpeaker() {
    }

    public static void say(AIPlayerEntity bot, String text) {
        if (bot == null || text == null || text.isBlank()) {
            return;
        }
        String cleaned = FunctionTagSanitizer.clean(text);
        if (cleaned.isBlank()) {
            return;
        }
        Text line = Text.literal("<" + bot.getGameProfile().getName() + "> " + cleaned);
        bot.getServer().execute(() -> bot.getServer().getPlayerManager().getPlayerList().forEach(player -> {
            if (!(player instanceof AIPlayerEntity)) {
                player.sendMessage(line, false);
            }
        }));
    }
}
