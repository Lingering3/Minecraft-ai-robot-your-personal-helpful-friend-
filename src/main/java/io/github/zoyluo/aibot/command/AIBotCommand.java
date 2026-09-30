package io.github.zoyluo.aibot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zoyluo.aibot.auth.BotAuthorizationGate;
import io.github.zoyluo.aibot.auth.BotAuthorizationPolicy;
import io.github.zoyluo.aibot.manager.AIPlayerManager;
import io.github.zoyluo.aibot.task.BuildTask;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.world.GameMode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AIBotCommand {
    private AIBotCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("aibot")
                .then(literal("spawn")
                        .then(argument("name", StringArgumentType.word())
                                .executes(context -> spawn(context.getSource(), StringArgumentType.getString(context, "name"), "worker"))
                                .then(argument("role", StringArgumentType.word())
                                        .executes(context -> spawn(context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "role"))))))
                .then(literal("role")
                        .then(argument("name", StringArgumentType.word())
                                .then(argument("role", StringArgumentType.word())
                                        .executes(context -> role(context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "role"))))))
                .then(literal("trigger")
                        .then(argument("name", StringArgumentType.word())
                                .executes(context -> triggerStatus(context.getSource(),
                                        StringArgumentType.getString(context, "name")))
                                .then(argument("trigger", StringArgumentType.greedyString())
                                        .executes(context -> trigger(context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "trigger"))))))
                .then(literal("buildrate")
                        .executes(context -> buildRateStatus(context.getSource()))
                        .then(argument("blocks_per_tick", IntegerArgumentType.integer(1, 4096))
                                .executes(context -> buildRate(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "blocks_per_tick")))))
                .then(literal("buildfoundation")
                        .executes(context -> buildFoundationStatus(context.getSource()))
                        .then(literal("fill")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> buildFoundationFill(context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))))
                        .then(literal("air")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> buildFoundationAir(context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled"))))))
                .then(literal("despawn")
                        .then(argument("name", StringArgumentType.word())
                                .executes(context -> despawn(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(literal("list")
                        .executes(context -> list(context.getSource())))
                .then(literal("blueprints")
                        .executes(context -> AIBotBlueprintSubcommand.list(context.getSource())))
                .then(AIBotBrainSubcommand.build())
                .then(AIBotLogSubcommand.build())
                .then(AIBotPersistSubcommand.build())
                .then(AIBotJobSubcommand.build())
                .then(AIBotMemorySubcommand.build())
                .then(AIBotObserveSubcommand.profile())
                .then(AIBotObserveSubcommand.replay())
                .then(AIBotObserveSubcommand.tps())
                .then(AIBotTaskSubcommand.build())
                .then(AIBotDeplintSubcommand.build())
                .then(AIBotBlueprintSubcommand.build())
                .then(AIBotSnapshotSubcommand.build()));
    }

    private static int spawn(ServerCommandSource source, String name, String role) {
        if (!BotAuthorizationGate.INSTANCE.canProvisionPersonalBot(source, "command:spawn")) {
            return 0;
        }
        ServerPlayerEntity executor = source.getPlayer();
        GameMode gameMode = executor == null ? GameMode.SURVIVAL : executor.interactionManager.getGameMode();
        UUID ownerUuid = executor == null ? null : executor.getUuid();
        if (ownerUuid != null && AIPlayerManager.INSTANCE.botOf(ownerUuid).isPresent()) {
            source.sendError(Text.literal("[AIBot] 你已经有一个 AI 助手了,请先 /aibot despawn <名字>"));
            return 0;
        }
        var rotation = source.getRotation();
        var spawned = AIPlayerManager.INSTANCE.spawn(
                source.getServer(),
                name,
                source.getWorld(),
                source.getPosition(),
                rotation.y,
                rotation.x,
                gameMode,
                ownerUuid);

        if (spawned.isPresent()) {
            AIPlayerManager.INSTANCE.setRole(spawned.get(), role);
            source.sendFeedback(() -> Text.literal("[AIBot] Spawned " + name + " role=" + AIPlayerManager.INSTANCE.role(spawned.get())), true);
            return 1;
        }

        source.sendError(Text.literal("[AIBot] 无法生成 " + name + " (名称已存在或已达到限制)"));
        return 0;
    }

    private static int role(ServerCommandSource source, String name, String role) {
        var bot = BotAuthorizationGate.INSTANCE.resolveAuthorized(
                source, name, BotAuthorizationPolicy.Operation.ADMIN, "command:role");
        if (bot.isEmpty()) {
            return 0;
        }
        AIPlayerManager.INSTANCE.setRole(bot.get(), role);
        source.sendFeedback(() -> Text.literal("[AIBot] " + name + " role=" + AIPlayerManager.INSTANCE.role(bot.get())), false);
        return 1;
    }

    private static int trigger(ServerCommandSource source, String name, String trigger) {
        var bot = BotAuthorizationGate.INSTANCE.resolveAuthorized(
                source, name, BotAuthorizationPolicy.Operation.ADMIN, "command:trigger");
        if (bot.isEmpty()) {
            return 0;
        }
        AIPlayerManager.INSTANCE.setTrigger(bot.get(), trigger);
        source.sendFeedback(() -> Text.literal("[AIBot] " + name + " trigger=" + AIPlayerManager.INSTANCE.trigger(bot.get())), false);
        return 1;
    }

    private static int triggerStatus(ServerCommandSource source, String name) {
        var bot = BotAuthorizationGate.INSTANCE.resolveAuthorized(
                source, name, BotAuthorizationPolicy.Operation.VIEW, "command:trigger_status");
        if (bot.isEmpty()) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal("[AIBot] " + name + " trigger=" + AIPlayerManager.INSTANCE.trigger(bot.get())), false);
        return 1;
    }

    private static int buildRate(ServerCommandSource source, int blocksPerTick) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:buildrate")) {
            return 0;
        }
        BuildTask.setPseudoPlaceBlocksPerTick(blocksPerTick);
        source.sendFeedback(() -> Text.literal("[AIBot] local pseudo build rate="
                + BuildTask.pseudoPlaceBlocksPerTick() + " blocks/tick"), false);
        return 1;
    }

    private static int buildRateStatus(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("[AIBot] local pseudo build rate="
                + BuildTask.pseudoPlaceBlocksPerTick() + " blocks/tick"), false);
        return 1;
    }

    private static int buildFoundationFill(ServerCommandSource source, boolean enabled) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:buildfoundation")) {
            return 0;
        }
        BuildTask.setFlattenFillEnabled(enabled);
        return buildFoundationStatus(source);
    }

    private static int buildFoundationAir(ServerCommandSource source, boolean enabled) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:buildfoundation")) {
            return 0;
        }
        BuildTask.setFlattenAirClearEnabled(enabled);
        return buildFoundationStatus(source);
    }

    private static int buildFoundationStatus(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("[AIBot] build foundation fill="
                + onOff(BuildTask.flattenFillEnabled())
                + " air=" + onOff(BuildTask.flattenAirClearEnabled())
                + " (fill=补地基, air=清空建筑空间/空气地基)"), false);
        return 1;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private static int despawn(ServerCommandSource source, String name) {
        var bot = BotAuthorizationGate.INSTANCE.resolveAuthorized(
                source, name, BotAuthorizationPolicy.Operation.ADMIN, "command:despawn");
        if (bot.isEmpty()) {
            return 0;
        }
        boolean removed = AIPlayerManager.INSTANCE.despawn(source.getServer(), name);
        if (removed) {
            source.sendFeedback(() -> Text.literal("[AIBot] Despawned " + name), true);
            return 1;
        }

        source.sendError(Text.literal("[AIBot] No such bot: " + name));
        return 0;
    }

    private static int list(ServerCommandSource source) {
        var bots = AIPlayerManager.INSTANCE.all().stream()
                .filter(bot -> BotAuthorizationGate.INSTANCE.canView(source, bot))
                .toList();
        String names = bots.stream()
                .map(player -> player.getGameProfile().getName() + "(" + AIPlayerManager.INSTANCE.role(player)
                        + ", trigger=" + AIPlayerManager.INSTANCE.trigger(player) + ")")
                .collect(Collectors.joining(", "));
        source.sendFeedback(() -> Text.literal("[AIBot] " + bots.size() + " bot(s): " + names), false);
        return bots.size();
    }
}
