package io.github.zoyluo.aibot.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zoyluo.aibot.AIBotConfig;
import io.github.zoyluo.aibot.auth.BotAuthorizationGate;
import io.github.zoyluo.aibot.auth.BotAuthorizationPolicy;
import io.github.zoyluo.aibot.blueprint.BlueprintCatalog;
import io.github.zoyluo.aibot.blueprint.LitematicaImporter;
import io.github.zoyluo.aibot.blueprint.RemoteBlueprints;
import io.github.zoyluo.aibot.blueprint.StructureImporter;
import io.github.zoyluo.aibot.item.BlueprintItem;
import io.github.zoyluo.aibot.item.BlueprintItems;
import io.github.zoyluo.aibot.log.BotLog;
import io.github.zoyluo.aibot.task.BlueprintLoader;
import io.github.zoyluo.aibot.task.BlueprintSchema;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /aibot blueprint 蓝图管理:list 列出本地蓝图(given 结构方块蓝图),give 发放蓝图物品。
 */
public final class AIBotBlueprintSubcommand {
    private AIBotBlueprintSubcommand() {
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> build() {
        return literal("blueprint")
                .then(literal("list").executes(context -> list(context.getSource())))
                .then(literal("give")
                        .then(argument("id", StringArgumentType.string())
                                .suggests((context, builder) -> suggestBlueprintIds(builder))
                                .executes(context -> give(context.getSource(),
                                        StringArgumentType.getString(context, "id")))
                                .then(argument("player", StringArgumentType.word())
                                        .executes(context -> give(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "player"))))))
                .then(literal("info")
                        .then(argument("id", StringArgumentType.string())
                                .suggests((context, builder) -> suggestBlueprintIds(builder))
                                .executes(context -> info(context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(literal("reload").executes(context -> reload(context.getSource())))
                .then(literal("remote")
                        .executes(context -> showRemote(context.getSource()))
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setRemote(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(literal("required")
                        .then(argument("value", BoolArgumentType.bool())
                                .executes(context -> setRequired(context.getSource(),
                                        BoolArgumentType.getBool(context, "value"))))
                        .executes(context -> showRequired(context.getSource())));
    }

    public static int list(ServerCommandSource source) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:list")) {
            return 0;
        }
        var entries = BlueprintCatalog.entries();
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            BlueprintCatalog.BlueprintEntry entry = entries.get(i);
            if (!catalog.isEmpty()) {
                catalog.append("\n");
            }
            catalog.append(i + 1)
                    .append(". ")
                    .append(BlueprintCatalog.chineseName(entry))
                    .append(" | id=\"")
                    .append(entry.id())
                    .append("\" | size=")
                    .append(sizeOf(entry.id()));
        }
        source.sendFeedback(() -> Text.literal("[AIBot] 可选建筑蓝图(" + entries.size()
                + ", 来源=" + blueprintSourceText() + "):\n" + catalog
                + "\n使用: /aibot blueprint info \"蓝图id\" 或 /aibot blueprint give \"蓝图id\""
                + "\n远程蓝图: /aibot blueprint remote true|false (默认 false,只用本地)"), false);
        return entries.size();
    }

    private static int give(ServerCommandSource source, String id) {
        return give(source, id, source.getName());
    }

    private static int give(ServerCommandSource source, String id, String playerName) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:give")) {
            return 0;
        }
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) {
            source.sendError(Text.literal("[AIBot] 找不到玩家: " + playerName));
            return 0;
        }
        try {
            BlueprintLoader.load(id);
        } catch (IOException exception) {
            source.sendError(Text.literal("[AIBot] 蓝图不存在或无法加载: " + id + " ("
                    + exception.getMessage() + ")"));
            return 0;
        }
        ItemStack stack = new ItemStack(BlueprintItems.BLUEPRINT);
        BlueprintItem.withBlueprint(stack, id);
        player.getInventory().offerOrDrop(stack);
        BotLog.commSystem("blueprint_given", "player", playerName, "blueprint", id);
        source.sendFeedback(() -> Text.literal("[AIBot] 已给 " + playerName + " 发放蓝图《" + id
                + "》。手持蓝图: 中键锁定位置 → R 键旋转 → 再次中键开始搭建。"), true);
        return 1;
    }

    private static int info(ServerCommandSource source, String id) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:info")) {
            return 0;
        }
        try {
            BlueprintSchema schema = BlueprintLoader.load(id);
            source.sendFeedback(() -> Text.literal("[AIBot] 蓝图 " + id
                    + " size=" + schema.width() + "x" + schema.height() + "x" + schema.depth()
                    + " placements=" + schema.placements().size()), false);
            return 1;
        } catch (IOException exception) {
            source.sendError(Text.literal("[AIBot] 蓝图不存在或无法加载: " + id));
            return 0;
        }
    }

    private static int showRequired(ServerCommandSource source) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:required")) {
            return 0;
        }
        boolean required = AIBotConfig.get().creative().isBlueprintRequired();
        source.sendFeedback(() -> Text.literal("[AIBot] 创造模式搭建" +
                (required ? "需要蓝图(默认)。" : "无需蓝图,直接搭建。") +
                " 用 /aibot blueprint required <true|false> 切换。"), false);
        return 1;
    }

    private static int setRequired(ServerCommandSource source, boolean value) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:required")) {
            return 0;
        }
        AIBotConfig.get().withCreative(new AIBotConfig.Creative(value));
        boolean now = AIBotConfig.get().creative().isBlueprintRequired();
        source.sendFeedback(() -> Text.literal("[AIBot] 已设置: 创造模式搭建" +
                (now ? "需要蓝图(默认)。" : "无需蓝图,直接搭建。")), true);
        return 1;
    }

    private static int reload(ServerCommandSource source) {        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:reload")) {
            return 0;
        }
        RemoteBlueprints.clearCache();
        BlueprintCatalog.refreshDisplayIndex();
        int structures = StructureImporter.listStructures().size();
        int litematics = LitematicaImporter.listLitematics().size();
        int remote = AIBotConfig.get().remoteBlueprints().isEnabled() ? RemoteBlueprints.entries().size() : 0;
        source.sendFeedback(() -> Text.literal("[AIBot] 已重新扫描蓝图库并刷新 blueprints/index.json: blueprints/structures/*.nbt=" + structures
                + " + blueprints/structures/*.litematic=" + litematics
                + " + 远程清单=" + remote
                + " (远程蓝图选中后会下载到 游戏目录/blueprints/structures/ 再按 Litematic 加载)"), true);
        return 1;
    }

    private static String sizeOf(String id) {
        if (RemoteBlueprints.has(id) && !LitematicaImporter.existsLocal(id)) {
            return RemoteBlueprints.sizeText(id) + ", remote";
        }
        try {
            BlueprintSchema schema = BlueprintLoader.load(id);
            return schema.width() + "x" + schema.height() + "x" + schema.depth()
                    + ", blocks=" + schema.placements().size();
        } catch (IOException exception) {
            return "?";
        }
    }

    private static int showRemote(ServerCommandSource source) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:remote")) {
            return 0;
        }
        AIBotConfig.RemoteBlueprints remote = AIBotConfig.get().remoteBlueprints();
        source.sendFeedback(() -> Text.literal("[AIBot] 远程蓝图搜索="
                + (remote.isEnabled() ? "开启" : "关闭")
                + "，当前来源=" + blueprintSourceText()
                + "，manifest=" + remote.manifestUrl()
                + "。切换: /aibot blueprint remote true|false"), false);
        return 1;
    }

    private static int setRemote(ServerCommandSource source, boolean enabled) {
        if (!BotAuthorizationGate.INSTANCE.requireGlobalAdmin(source, "command:blueprint:remote")) {
            return 0;
        }
        AIBotConfig.RemoteBlueprints current = AIBotConfig.get().remoteBlueprints();
        AIBotConfig.get().withRemoteBlueprints(new AIBotConfig.RemoteBlueprints(
                enabled,
                current.manifestUrl(),
                current.cacheSeconds(),
                current.downloadTimeoutSeconds(),
                current.maxFileSizeMb()));
        RemoteBlueprints.clearCache();
        source.sendFeedback(() -> Text.literal("[AIBot] 已设置蓝图来源: "
                + blueprintSourceText()
                + "。远程蓝图只拉清单,选中后下载 .litematic 到 blueprints/structures/ 再建造。"), true);
        return 1;
    }

    private static String blueprintSourceText() {
        return AIBotConfig.get().remoteBlueprints().isEnabled() ? "本地+云端清单" : "本地";
    }

    private static CompletableFuture<Suggestions> suggestBlueprintIds(SuggestionsBuilder builder) {
        String remaining = unquote(builder.getRemaining()).toLowerCase(Locale.ROOT);
        for (BlueprintCatalog.BlueprintEntry entry : BlueprintCatalog.entries()) {
            String id = entry.id();
            String lower = id.toLowerCase(Locale.ROOT);
            String chinese = BlueprintCatalog.chineseName(entry).toLowerCase(Locale.ROOT);
            if (remaining.isBlank()
                    || lower.startsWith(remaining)
                    || chinese.startsWith(remaining)
                    || acronym(lower).startsWith(remaining)) {
                builder.suggest(quoteIfNeeded(id),
                        Text.literal(BlueprintCatalog.chineseName(entry) + " | " + id));
            }
        }
        return builder.buildFuture();
    }

    private static String quoteIfNeeded(String id) {
        if (id.indexOf(' ') < 0) {
            return id;
        }
        return "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String acronym(String value) {
        StringBuilder builder = new StringBuilder();
        boolean take = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (take) {
                    builder.append(c);
                }
                take = false;
            } else {
                take = true;
            }
        }
        return builder.toString();
    }
}
