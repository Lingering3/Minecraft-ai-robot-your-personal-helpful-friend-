package io.github.zoyluo.aibot.brain;

import io.github.zoyluo.aibot.auth.BotAuthorizationGate;
import io.github.zoyluo.aibot.auth.BotAuthorizationPolicy;
import io.github.zoyluo.aibot.entity.AIPlayerEntity;
import io.github.zoyluo.aibot.manager.AIPlayerManager;
import io.github.zoyluo.aibot.log.BotLog;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ChatCaptureListener {
    private static final Pattern MENTION = Pattern.compile("@(\\w+)\\s+(.+)");

    private ChatCaptureListener() {
    }

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString();
            var matcher = MENTION.matcher(text);
            if (matcher.find()) {
                String targetName = matcher.group(1);
                String body = matcher.group(2);
                AIPlayerManager.INSTANCE.getByName(targetName).ifPresent(bot ->
                        dispatch(sender, bot, body, "chat:@bot"));
                return;
            }
            Optional<Target> triggerTarget = triggerTarget(text);
            if (triggerTarget.isPresent()) {
                Target target = triggerTarget.orElseThrow();
                dispatch(sender, target.bot(), target.body(), "chat:trigger");
                return;
            }
            if (singleLocalTarget(sender).isPresent()) {
                dispatch(sender, singleLocalTarget(sender).orElseThrow(), text, "chat:default_single");
            }
        });
    }

    private static Optional<Target> triggerTarget(String text) {
        String normalized = normalize(text);
        return AIPlayerManager.INSTANCE.all().stream()
                .map(bot -> new Target(bot, AIPlayerManager.INSTANCE.trigger(bot), text))
                .filter(target -> !target.trigger().isBlank())
                .filter(target -> normalized.contains(normalize(target.trigger())))
                .max(Comparator.comparingInt(target -> target.trigger().length()))
                .map(target -> new Target(target.bot(), target.trigger(), stripTrigger(text, target.trigger())));
    }

    private static Optional<AIPlayerEntity> singleLocalTarget(ServerPlayerEntity sender) {
        if (humanPlayerCount(sender) != 1 || AIPlayerManager.INSTANCE.all().size() != 1) {
            return Optional.empty();
        }
        return AIPlayerManager.INSTANCE.all().stream().findFirst();
    }

    private static int humanPlayerCount(ServerPlayerEntity sender) {
        return (int) sender.getServer().getPlayerManager().getPlayerList().stream()
                .filter(player -> !(player instanceof AIPlayerEntity))
                .count();
    }

    private static void dispatch(ServerPlayerEntity sender, AIPlayerEntity bot, String body, String channel) {
        if (body == null || body.isBlank()) {
            return;
        }
        if (!BotAuthorizationGate.INSTANCE.authorize(
                sender, bot, BotAuthorizationPolicy.Operation.COMMAND, channel)) {
            return;
        }
        BotLog.comm(bot, "chat_in", "sender", sender.getGameProfile().getName(), "text", body, "channel", channel);
        if (io.github.zoyluo.aibot.runtime.IntentController.INSTANCE.routePlayerControlPhrase(
                bot, io.github.zoyluo.aibot.runtime.IntentController.ControlOrigin.PLAYER_COMMAND, body)) {
            return;
        }
        BrainCoordinator.INSTANCE.handleMessage(bot, sender.getGameProfile().getName(), body);
    }

    private static String stripTrigger(String text, String trigger) {
        String normalizedText = normalize(text);
        String normalizedTrigger = normalize(trigger);
        int index = normalizedText.indexOf(normalizedTrigger);
        if (index < 0) {
            return text;
        }
        String stripped = text.substring(0, index) + text.substring(Math.min(text.length(), index + trigger.length()));
        stripped = stripped.replaceFirst("^[\\s,:：，]+", "").replaceFirst("[\\s,:：，]+$", "");
        return stripped.isBlank() ? text : stripped;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record Target(AIPlayerEntity bot, String trigger, String body) {
    }
}
