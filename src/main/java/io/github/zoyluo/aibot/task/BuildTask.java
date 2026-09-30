package io.github.zoyluo.aibot.task;

import io.github.zoyluo.aibot.action.ActionResult;
import io.github.zoyluo.aibot.action.BuildAction;
import io.github.zoyluo.aibot.action.InventoryAction;
import io.github.zoyluo.aibot.action.LookAction;
import io.github.zoyluo.aibot.action.MaterialPalette;
import io.github.zoyluo.aibot.action.MiningAction;
import io.github.zoyluo.aibot.brain.BotSpeaker;
import io.github.zoyluo.aibot.entity.AIPlayerEntity;
import io.github.zoyluo.aibot.goal.StructureVerifier;
import io.github.zoyluo.aibot.log.BotLog;
import io.github.zoyluo.aibot.manager.AIPlayerManager;
import io.github.zoyluo.aibot.mode.CapabilityRuntime;
import io.github.zoyluo.aibot.mode.ObservableWorldQuery;
import io.github.zoyluo.aibot.mode.PrivilegedCapability;
import io.github.zoyluo.aibot.pathfinding.Standability;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.util.Hand;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.world.GameMode;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;

public final class BuildTask extends AbstractTask {
    private enum Phase {
        SITE,
        APPROACH,
        FLATTEN,
        BUILD
    }

    private enum FlattenKind {
        CLEAR,
        FILL
    }

    private final BlueprintSchema blueprint;
    private BlockPos anchor;
    private final boolean autoSite;
    private final boolean flatten;
    private final Deque<FlattenTarget> flattenTargets = new ArrayDeque<>();
    private Phase phase = Phase.SITE;
    private FlattenTarget currentFlattenTarget;
    private int flattenTargetTick;   // 当前整地格起算 tick:够不到的格超预算即跳过,防 nearbyStand 退化"走向自己"死循环
    private int nextIndex;
    private int buildTargetTick;      // 当前落块格起算 tick:放不到的块超预算即跳过(best-effort),防 moveWithinReach 永续寻路空转
    private int buildTargetIndex = -1;
    private int retryTicks;
    private int placeDelayTicks;
    private int placedBlocks;
    private int skippedBlocks;
    private boolean pseudoPlacement;
    private boolean materialReportSent;
    private boolean flattenMiningStarted;
    private boolean buildMiningStarted;
    private BlockPos approachTarget;
    private int approachStartTick;
    private String note = "";
    private static volatile int pseudoPlaceBlocksPerTick = Math.max(1,
            Integer.getInteger("aibot.pseudoPlaceRate", 8));
    private static volatile boolean flattenFillEnabled = Boolean.parseBoolean(
            System.getProperty("aibot.buildFoundationFill", "true"));
    private static volatile boolean flattenAirClearEnabled = Boolean.parseBoolean(
            System.getProperty("aibot.buildFoundationAir", "true"));
    private static final int BASE_TIMEOUT_TICKS = 16000;
    private static final int PSEUDO_TIMEOUT_PADDING_TICKS = 1200;
    private static final int REAL_TIMEOUT_PER_BLOCK_TICKS = 120;
    private static final int MATERIAL_SUMMARY_LIMIT = 12;

    public BuildTask(BlueprintSchema blueprint, BlockPos anchor) {
        this(blueprint, anchor, false, false);
    }

    public BuildTask(BlueprintSchema blueprint, BlockPos anchor, boolean autoSite, boolean flatten) {
        this.blueprint = blueprint;
        this.anchor = anchor == null ? null : anchor.toImmutable();
        this.autoSite = autoSite;
        this.flatten = flatten;
    }

    @Override
    public String name() {
        return "build";
    }

    @Override
    public String describe() {
        String anchorText = anchor == null ? "auto" : compact(anchor);
        return "Building " + blueprint.name() + " at " + anchorText + " " + nextIndex + "/" + blueprint.placements().size()
                + " phase=" + phase + (note.isBlank() ? "" : " note=" + note);
    }

    @Override
    public double progress() {
        if (state == TaskState.COMPLETED) {
            return 1.0D;
        }
        double buildProgress = blueprint.placements().isEmpty() ? 1.0D : Math.min(1.0D, (double) nextIndex / blueprint.placements().size());
        return switch (phase) {
            case SITE -> 0.0D;
            case APPROACH -> 0.05D;
            case FLATTEN -> Math.min(0.25D, flattenTargets.isEmpty() ? 0.25D : 0.10D);
            case BUILD -> 0.25D + buildProgress * 0.75D;
        };
    }

    @Override
    public boolean isWaiting() {
        // 建造=原地立着逐格挖/放(整地+砌房),位置长时间不变是正常作业,不是卡死。
        // 不豁免则 StuckWatcher 200t 位置不变即误杀(real_build 实测 task_stuck_aborted reason=stuck:build,
        // 真实地形整地阶段静立施工被斩,phase=FLATTEN progress=0.1)。卡死保护交本任务 build_timeout(7200t)+
        // moveWithinReach 自身 retryTicks(寻路真失败才计)兜底,比 StuckWatcher 更懂建造语义。
        return true;
    }

    @Override
    protected void onStart(AIPlayerEntity bot) {
        nextIndex = 0;
        buildTargetTick = 0;
        buildTargetIndex = -1;
        retryTicks = 0;
        placeDelayTicks = 0;
        placedBlocks = 0;
        skippedBlocks = 0;
        pseudoPlacement = !bot.getServer().isDedicated();
        materialReportSent = false;
        flattenTargets.clear();
        currentFlattenTarget = null;
        flattenMiningStarted = false;
        buildMiningStarted = false;
        approachTarget = null;
        approachStartTick = 0;
        phase = Phase.SITE;
    }

    @Override
    protected void onTick(AIPlayerEntity bot) {
        if (elapsed > timeoutTicks()) {
            // 真实地形建房=整地(挖高填低)+逐格砌 100+ 块,且重活拖低 tps;7200t 不够(实测只到 81/116)。
            // 基线保留 16000t;大型 litematic 投影按块数扩展预算,避免几千块结构必然超时。
            fail("build_timeout");
            return;
        }
        if (placeDelayTicks > 0) {
            placeDelayTicks--;
            return;
        }
        switch (phase) {
            case SITE -> site(bot);
            case APPROACH -> approach(bot);
            case FLATTEN -> flatten(bot);
            case BUILD -> build(bot);
        }
    }

    private void site(AIPlayerEntity bot) {
        if (anchor == null) {
            if (!autoSite) {
                fail("missing_anchor");
                return;
            }
            // flatten 开启时用 lenient 选址:真实起伏地形罕有现成平地,选最平可用点交 FLATTEN 整平
            //(治 real_build no_flat_site 5/10);flatten 关闭时严格(平整画布零回归)。
            anchor = SiteFinder.findSite(bot, blueprint.width(), blueprint.depth(), 16, flatten).orElse(null);
            if (anchor == null) {
                fail("no_flat_site");
                return;
            }
            note = "auto_site=" + compact(anchor);
        }
        reportMaterials(bot);
        if (flatten) {
            planFlatten(bot);
            phase = Phase.FLATTEN;
        } else {
            phase = Phase.APPROACH;
        }
    }

    /**
     * 接近阶段(伪放置专用):让 bot 先走到建造位置旁边,再开始逐块放置。
     * 真实放置模式由 build/place 自身的 moveWithinReach 处理接近,这里直接放行。
     * 站位取蓝图居中、外边缘前方 1 格,并用 Standability 找最近可站立点兜底。
     */
    private void approach(AIPlayerEntity bot) {
        if (!pseudoPlacement) {
            phase = Phase.BUILD;
            return;
        }
        if (approachTarget == null) {
            int centerX = anchor.getX() + Math.max(0, blueprint.width() / 2);
            BlockPos preferred = new BlockPos(centerX, anchor.getY(), anchor.getZ() - 1);
            approachTarget = Standability.findNearestStandable(
                    bot.getServerWorld(), preferred, 6, 4, 4).orElse(preferred);
            approachStartTick = elapsed;
            bot.getActionPack().startPathTo(approachTarget);
        }
        double distSq = bot.getPos().squaredDistanceTo(
                approachTarget.getX() + 0.5D, approachTarget.getY(), approachTarget.getZ() + 0.5D);
        boolean pathIdle = bot.getActionPack().isPathExecutorIdle();
        if ((distSq < 4.0D && pathIdle) || elapsed - approachStartTick > 600) {
            bot.getActionPack().stopAll();
            phase = Phase.BUILD;
        } else if (pathIdle && distSq >= 4.0D) {
            // 寻路提前结束但尚未到达,重新发起一次
            bot.getActionPack().startPathTo(approachTarget);
        }
    }

    private void planFlatten(AIPlayerEntity bot) {
        ServerWorld world = bot.getServerWorld();
        boolean rawTerrainRead = CapabilityRuntime.decide(
                bot, PrivilegedCapability.HIDDEN_BLOCK_SCAN, "build_flatten_plan").allowed();
        flattenTargets.clear();
        for (int dx = 0; dx < blueprint.width(); dx++) {
            for (int dz = 0; dz < blueprint.depth(); dz++) {
                BlockPos ground = anchor.add(dx, -1, dz);
                if (flattenFillEnabled
                        && (!rawTerrainRead
                        || world.getBlockState(ground).isAir()
                        || !world.getFluidState(ground).isEmpty())) {
                    flattenTargets.addLast(new FlattenTarget(ground, FlattenKind.FILL));
                }
                if (flattenAirClearEnabled) {
                    for (int dy = 0; dy < Math.max(blueprint.height(), 1); dy++) {
                        BlockPos clear = anchor.add(dx, dy, dz);
                        if (!rawTerrainRead
                                || (!world.getBlockState(clear).isAir() && world.getFluidState(clear).isEmpty())) {
                            flattenTargets.addLast(new FlattenTarget(clear, FlattenKind.CLEAR));
                        }
                    }
                }
            }
        }
    }

    private void flatten(AIPlayerEntity bot) {
        if (pseudoPlacement) {
            pseudoFlatten(bot);
            return;
        }
        if (currentFlattenTarget == null) {
            currentFlattenTarget = flattenTargets.pollFirst();
            flattenMiningStarted = false;
            flattenTargetTick = elapsed;
            retryTicks = 0;
            if (currentFlattenTarget == null) {
                phase = Phase.APPROACH;
                return;
            }
        }
        // 整地格预算:够不到的格(nearbyStand 无落脚点会退化成 startPathTo 自己→原地死循环,real_build 实测
        // path_idle 0 进度 build_timeout)→ 50t 内没搞定就跳过,best-effort 继续整地/盖房,站不到的格不强求。
        if (elapsed - flattenTargetTick > 50) {
            note = "flatten_skip=" + compact(currentFlattenTarget.pos()); // 够不到的整地格跳过(防原地死循环)
            currentFlattenTarget = null;
            return;
        }
        if (currentFlattenTarget.kind() == FlattenKind.CLEAR) {
            clearFlattenBlock(bot);
        } else {
            fillFlattenBlock(bot);
        }
    }

    private void pseudoFlatten(AIPlayerEntity bot) {
        int budget = Math.max(1, pseudoPlaceBlocksPerTick);
        for (int changed = 0; changed < budget; changed++) {
            FlattenTarget target = flattenTargets.pollFirst();
            if (target == null) {
                Standability.clearCache();
                phase = Phase.APPROACH;
                return;
            }
            Block block = target.kind() == FlattenKind.CLEAR ? Blocks.AIR : Blocks.DIRT;
            LookAction.lookAt(bot, target.pos().toCenterPos());
            bot.swingHand(Hand.MAIN_HAND);
            bot.updateLastActionTime();
            BlockState before = bot.getServerWorld().getBlockState(target.pos());
            BlockState after = block.getDefaultState();
            if (bot.getServerWorld().setBlockState(target.pos(), after, Block.NOTIFY_ALL)) {
                playMutationSound(bot, target.pos(), before, after);
            }
        }
        placeDelayTicks = 1;
    }

    private void clearFlattenBlock(AIPlayerEntity bot) {
        BlockPos pos = currentFlattenTarget.pos();
        if (!ensureObservableWorkPose(bot, pos, "flatten_clear")) {
            return;
        }
        if (bot.getServerWorld().getBlockState(pos).isAir()) {
            Standability.clearCache();
            currentFlattenTarget = null;
            retryTicks = 0;
            return;
        }
        if (!flattenMiningStarted && bot.getActionPack().isMiningIdle()) {
            ActionResult result = MiningAction.startMining(bot, pos, Direction.getFacing(bot.getEyePos().subtract(pos.toCenterPos())));
            if (result.isFailed()) {
                fail(result.reason());
                return;
            }
            flattenMiningStarted = true;
        }
    }

    private void fillFlattenBlock(AIPlayerEntity bot) {
        BlockPos pos = currentFlattenTarget.pos();
        if (!ensureObservableWorkPose(bot, pos, "flatten_fill")) {
            return;
        }
        if (!bot.getServerWorld().getBlockState(pos).isAir()) {
            Standability.clearCache();
            currentFlattenTarget = null;
            retryTicks = 0;
            return;
        }
        OptionalInt slot = MaterialPalette.pickSlot(bot, "dirt_like");
        if (slot.isEmpty()) {
            slot = MaterialPalette.pickAnyBlockSlot(bot);
        }
        if (slot.isEmpty()) {
            fail("missing_flatten_material");
            return;
        }
        InventoryAction.equipFromSlot(bot, slot.getAsInt());
        ActionResult result = BuildAction.placeBlockAt(bot, pos);
        if (result.isSuccess()) {
            Standability.clearCache();
            currentFlattenTarget = null;
            retryTicks = 0;
            placeDelayTicks = 2;
            return;
        }
        retryTicks++;
        BlockPos stand = nearbyStand(bot, pos);
        if (stand != null && bot.getActionPack().isPathExecutorIdle()) {
            bot.getActionPack().startPathTo(stand);
        }
        if (retryTicks > 12) {
            fail("flatten_fill_failed: " + result.reason() + " at " + compact(pos));
        }
    }

    private void build(AIPlayerEntity bot) {
        if (pseudoPlacement) {
            pseudoBuild(bot);
            return;
        }
        if (nextIndex >= blueprint.placements().size()) {
            var report = StructureVerifier.verify(
                    bot.getServerWorld(), blueprint, anchor, placedBlocks, skippedBlocks);
            bot.getActionPack().stopAll();
            int required = report.expected() - report.unsupported();
            if (report.mismatched() > 0 || report.matched() != required) {
                fail("structure_incomplete: matched=" + report.matched()
                        + "/" + required
                        + " skipped=" + report.skipped()
                        + " unsupported=" + report.unsupported());
            } else {
                complete();
            }
            return;
        }
        BlueprintSchema.BlockPlacement placement = blueprint.placements().get(nextIndex);
        BlockPos pos = anchor.add(placement.dx(), placement.dy(), placement.dz());
        // Registry validation is pure and must happen before visibility/path budgets. Otherwise an
        // invalid ID can be skipped while still unseen and reach terminal structure verification,
        // where it used to surface as an uncaught Identifier exception.
        Block block = resolveBlock(placement.blockId());
        if (block == null) {
            // 无对应原版方块: 跳过该方块,继续搭建其余结构
            skipBuildTarget(bot, pos, "unknown_block:" + placement.blockId());
            return;
        }
        // 落块格预算(镜像 flatten 50t skip):同一块连续放不到时先记录并继续其余结构，避免单格把
        // 整个执行器卡到 build_timeout。末尾按完整 blueprint（包括 AIR）核验世界状态；只有 exact
        // match 才完成，否则以 structure_incomplete 失败，不能把 best-effort 误报为完工。
        if (nextIndex != buildTargetIndex) {
            buildTargetIndex = nextIndex;
            buildTargetTick = elapsed;
            retryTicks = 0;
            buildMiningStarted = false;
        } else if (elapsed - buildTargetTick > 80) {
            skipBuildTarget(bot, pos, "target_timeout");
            return;
        }
        if (!ensureObservableWorkPose(bot, pos, "build")) {
            return;
        }
        if (block == Blocks.AIR) {
            clearBlueprintAir(bot, pos);
            return;
        }
        if (bot.getServerWorld().getBlockState(pos).isOf(block)
                || (placement.palette() != null && MaterialPalette.matchesBlock(bot.getServerWorld().getBlockState(pos), placement.palette()))) {
            nextIndex++;
            return;
        }
        OptionalInt slot = materialSlot(bot, placement, block);
        if (slot.isEmpty()) {
            fail("missing_material: " + materialName(placement));
            return;
        }
        InventoryAction.equipFromSlot(bot, slot.getAsInt());
        ActionResult result = BuildAction.placeBlockAt(bot, pos);
        if (result.isSuccess()) {
            placedBlocks++;
            nextIndex++;
            retryTicks = 0;
            placeDelayTicks = 2;
            return;
        }
        retryTicks++;
        BlockPos stand = nearbyStand(bot, pos);
        if (stand != null && bot.getActionPack().isPathExecutorIdle()) {
            bot.getActionPack().startPathTo(stand);
        }
        placeDelayTicks = 5;
        if (retryTicks > 12) {
            // best-effort execution:单块反复失败时先继续其余结构；终态仍会因 skippedBlocks>0
            // 明确失败。与上面的 80t 预算双保险，谁先到谁记录该缺口。
            skipBuildTarget(bot, pos, "place_failed:" + result.reason());
        }
    }

    private void pseudoBuild(AIPlayerEntity bot) {
        int budget = Math.max(1, pseudoPlaceBlocksPerTick);
        for (int placedThisTick = 0; placedThisTick < budget; placedThisTick++) {
            if (nextIndex >= blueprint.placements().size()) {
                var report = StructureVerifier.verify(
                        bot.getServerWorld(), blueprint, anchor, placedBlocks, skippedBlocks);
                bot.getActionPack().stopAll();
                int required = report.expected() - report.unsupported();
                if (report.mismatched() > 0 || report.matched() != required) {
                    fail("structure_incomplete: matched=" + report.matched()
                            + "/" + required
                            + " skipped=" + report.skipped()
                            + " unsupported=" + report.unsupported());
                } else {
                    complete();
                }
                return;
            }
            BlueprintSchema.BlockPlacement placement = blueprint.placements().get(nextIndex);
            BlockPos pos = anchor.add(placement.dx(), placement.dy(), placement.dz());
            Block block = resolvePseudoBlock(placement);
            if (block == null) {
                // 无对应原版方块: 跳过该方块,继续搭建其余结构
                BotLog.action(bot, "build_block_skipped",
                        "index", nextIndex, "reason", "unknown_block:" + placement.blockId());
                skippedBlocks++;
                nextIndex++;
                continue;
            }
            if (block != Blocks.AIR && !isCreative(bot)) {
                OptionalInt slot = materialSlot(bot, placement, block);
                if (slot.isEmpty()) {
                    fail("missing_material: " + materialName(placement));
                    return;
                }
                consumePseudoMaterial(bot, slot.getAsInt());
            }
            LookAction.lookAt(bot, pos.toCenterPos());
            bot.swingHand(Hand.MAIN_HAND);
            bot.updateLastActionTime();
            BlockState before = bot.getServerWorld().getBlockState(pos);
            BlockState state = io.github.zoyluo.aibot.blueprint.BlockStateProperties.apply(
                    block.getDefaultState(), placement.stateProperties());
            if (!bot.getServerWorld().setBlockState(pos, state, Block.NOTIFY_ALL)) {
                // setBlockState 在目标位置已是相同状态时返回 false(门洞 air 占位、重复放置)。
                // 回读当前状态,若已等于目标状态则按幂等成功处理,而不是误判为致命失败。
                BlockState current = bot.getServerWorld().getBlockState(pos);
                if (current == state || current.equals(state)) {
                    Standability.clearCache();
                    placedBlocks++;
                    nextIndex++;
                    continue;
                }
                fail("pseudo_place_rejected: " + compact(pos));
                return;
            }
            playMutationSound(bot, pos, before, state);
            Standability.clearCache();
            placedBlocks++;
            nextIndex++;
        }
        placeDelayTicks = 1;
    }

    private void clearBlueprintAir(AIPlayerEntity bot, BlockPos pos) {
        if (!ensureObservableWorkPose(bot, pos, "build_air")) {
            return;
        }
        if (bot.getServerWorld().getBlockState(pos).isAir()) {
            if (buildMiningStarted) {
                placedBlocks++;
            }
            buildMiningStarted = false;
            nextIndex++;
            retryTicks = 0;
            return;
        }
        if (!bot.getActionPack().isMiningIdle()) {
            return;
        }
        if (buildMiningStarted) {
            buildMiningStarted = false;
            retryTicks++;
        }
        ActionResult result = MiningAction.startMining(
                bot, pos, Direction.getFacing(bot.getEyePos().subtract(pos.toCenterPos())));
        if (result.isFailed()) {
            retryTicks++;
            if (retryTicks > 12) {
                skipBuildTarget(bot, pos, "air_clear_failed:" + result.reason());
            }
            return;
        }
        buildMiningStarted = true;
    }

    private boolean ensureObservableWorkPose(AIPlayerEntity bot, BlockPos pos, String reason) {
        double reach = bot.getBlockInteractionRange();
        if (ObservableWorldQuery.canObserveCell(bot, pos)
                && moveWithinReach(bot, pos, reason, reach * reach)) {
            return true;
        }
        if (bot.getActionPack().isPathExecutorIdle()) {
            BlockPos stand = nearbyStand(bot, pos);
            if (stand != null) {
                ActionResult path = bot.getActionPack().startPathTo(stand);
                if (path.isFailed() && "pathfinding_throttled".equals(path.reason())) {
                    placeDelayTicks = 4;
                }
            }
        }
        return false;
    }

    private void skipBuildTarget(AIPlayerEntity bot, BlockPos pos, String reason) {
        bot.getActionPack().stopAll();
        BotLog.action(bot, "build_block_skipped",
                "index", nextIndex,
                "pos", compact(pos),
                "reason", reason);
        note = "build_skip=" + nextIndex + ":" + reason;
        skippedBlocks++;
        nextIndex++;
        retryTicks = 0;
        buildMiningStarted = false;
    }

    private OptionalInt materialSlot(AIPlayerEntity bot, BlueprintSchema.BlockPlacement placement, Block block) {
        if (placement.palette() != null && !placement.palette().isBlank()) {
            if (!MaterialPalette.isKnown(placement.palette())) {
                fail("unknown_palette: " + placement.palette());
                return OptionalInt.empty();
            }
            OptionalInt slot = MaterialPalette.pickSlot(bot, placement.palette());
            if (slot.isPresent()) {
                return slot;
            }
        }
        Item item = block.asItem();
        if (!(item instanceof BlockItem)) {
            fail("not_placeable_item: " + placement.blockId());
            return OptionalInt.empty();
        }
        return InventoryAction.findItem(bot, item);
    }

    private Block resolvePseudoBlock(BlueprintSchema.BlockPlacement placement) {
        if (placement.palette() != null && !placement.palette().isBlank()) {
            if (!MaterialPalette.isKnown(placement.palette())) {
                fail("unknown_palette: " + placement.palette());
                return null;
            }
            Item item = MaterialPalette.GROUPS.get(placement.palette()).stream()
                    .filter(BlockItem.class::isInstance)
                    .findFirst()
                    .orElse(null);
            if (item instanceof BlockItem blockItem) {
                return blockItem.getBlock();
            }
            fail("palette_not_placeable: " + placement.palette());
            return null;
        }
        return resolveBlock(placement.blockId());
    }

    private static void consumePseudoMaterial(AIPlayerEntity bot, int slot) {
        if (slot < 0 || slot >= bot.getInventory().size()) {
            return;
        }
        ItemStack stack = bot.getInventory().getStack(slot);
        if (!stack.isEmpty()) {
            stack.decrement(1);
            bot.getInventory().markDirty();
        }
    }

    private void reportMaterials(AIPlayerEntity bot) {
        if (materialReportSent) {
            return;
        }
        materialReportSent = true;
        if (isCreative(bot)) {
            return;
        }
        Map<String, Integer> materials = requiredMaterials();
        BotSpeaker.say(bot, "开始建造 " + blueprint.name() + "。需要: " + materialSummary(materials));
    }

    private Map<String, Integer> requiredMaterials() {
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (BlueprintSchema.BlockPlacement placement : blueprint.placements()) {
            if ("minecraft:air".equals(placement.blockId())) {
                continue;
            }
            String key = materialName(placement);
            materials.merge(key, 1, Integer::sum);
        }
        return materials;
    }

    private static String materialSummary(Map<String, Integer> materials) {
        if (materials.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        int hiddenKinds = 0;
        int hiddenTotal = 0;
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            if (shown >= MATERIAL_SUMMARY_LIMIT) {
                hiddenKinds++;
                hiddenTotal += entry.getValue();
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append(" x").append(entry.getValue());
            shown++;
        }
        if (hiddenKinds > 0) {
            builder.append(", ... plus ")
                    .append(hiddenKinds)
                    .append(" kinds x")
                    .append(hiddenTotal);
        }
        return builder.toString();
    }

    private int timeoutTicks() {
        int blockCount = blueprint.placements() == null ? 0 : blueprint.placements().size();
        int flattenCount = flattenTargets.size() + (currentFlattenTarget == null ? 0 : 1);
        if (pseudoPlacement) {
            int rate = Math.max(1, pseudoPlaceBlocksPerTick);
            long projected = (long) Math.ceil(blockCount / (double) rate) + flattenCount + PSEUDO_TIMEOUT_PADDING_TICKS;
            return (int) Math.min(Integer.MAX_VALUE, Math.max(BASE_TIMEOUT_TICKS, projected));
        }
        long projected = (long) BASE_TIMEOUT_TICKS
                + (long) blockCount * REAL_TIMEOUT_PER_BLOCK_TICKS
                + (long) flattenCount * 20L;
        return (int) Math.min(Integer.MAX_VALUE, projected);
    }

    private static boolean isCreative(AIPlayerEntity bot) {
        if (bot.interactionManager.getGameMode() == GameMode.CREATIVE
                || bot.getAbilities().creativeMode) {
            return true;
        }
        return AIPlayerManager.INSTANCE.ownerOf(bot)
                .map(uuid -> bot.getServer().getPlayerManager().getPlayer(uuid))
                .map(player -> player.interactionManager.getGameMode() == GameMode.CREATIVE
                        || player.getAbilities().creativeMode)
                .orElse(false);
    }

    private static void playMutationSound(AIPlayerEntity bot, BlockPos pos, BlockState before, BlockState after) {
        if (after.isAir()) {
            if (!before.isAir()) {
                var group = before.getSoundGroup();
                bot.getServerWorld().playSound(
                        null,
                        pos,
                        group.getBreakSound(),
                        SoundCategory.BLOCKS,
                        (group.getVolume() + 1.0F) / 2.0F,
                        group.getPitch());
            }
            return;
        }
        var group = after.getSoundGroup();
        bot.getServerWorld().playSound(
                null,
                pos,
                group.getPlaceSound(),
                SoundCategory.BLOCKS,
                (group.getVolume() + 1.0F) / 2.0F,
                group.getPitch() * 0.8F);
    }

    private Block resolveBlock(String blockId) {
        Identifier id;
        try {
            id = Identifier.of(blockId);
        } catch (RuntimeException exception) {
            // 无效 id: 不终止任务,交由调用方跳过该方块
            return null;
        }
        // 找不到对应方块(如模组方块): 返回 null, 由调用方跳过继续搭建
        return Registries.BLOCK.getOptionalValue(id).orElse(null);
    }

    private boolean moveWithinReach(AIPlayerEntity bot, BlockPos pos, String reason, double maxDistanceSquared) {
        // Never place a blueprint block through the bot's feet or head. Operator mode used to
        // hide this mistake with an emergency teleport; strict survival must first walk to a
        // real adjacent stand position.
        BlockPos feet = bot.getBlockPos();
        if (pos.equals(feet) || pos.equals(feet.up())) {
            if (bot.getActionPack().isPathExecutorIdle()) {
                BlockPos stand = nearbyStand(bot, pos);
                if (stand == null || stand.equals(feet)) {
                    fail("no_safe_stand_for_" + reason + ": " + compact(pos));
                    return false;
                }
                ActionResult path = bot.getActionPack().startPathTo(stand);
                if (path.isFailed() && !"pathfinding_throttled".equals(path.reason())) {
                    retryTicks++;
                    if (retryTicks > 12) {
                        fail("path_to_safe_stand_for_" + reason + "_failed: " + path.reason());
                    }
                }
            }
            return false;
        }
        // A path started to gain a safe angle is part of this placement attempt. Let it finish;
        // stopping it merely because the block is already within raw reach leaves the bot on the
        // same occluded side forever.
        if (!bot.getActionPack().isPathExecutorIdle()) {
            return false;
        }
        if (bot.getEyePos().squaredDistanceTo(pos.toCenterPos()) > maxDistanceSquared) {
            BlockPos stand = nearbyStand(bot, pos);
            if (stand == null) {
                fail("no_stand_position_for_" + reason + ": " + compact(pos));
                return false;
            }
            ActionResult path = bot.getActionPack().startPathTo(stand);
            if (path.isFailed()) {
                if ("pathfinding_throttled".equals(path.reason())) {
                    // 节流退避:寻路是全局速率限。整地逐块大量请求时每 tick 重请会【永远撞限流】→
                    // bot 到不了第一个整地目标、0 place/0 mine → build_timeout(real_build 实测 flatten 0 进度)。
                    // 退避 4 tick 让速率窗口清掉再重试(不计失败预算);onTick 顶部 placeDelayTicks>0 正好跳过。
                    placeDelayTicks = 4;
                } else {
                    // 真实无路(无 stand/障碍)才累计,>12 判死。
                    retryTicks++;
                    if (retryTicks > 12) {
                        fail("path_to_" + reason + "_failed: " + path.reason());
                    }
                }
            }
            return false;
        }
        return true;
    }

    private BlockPos nearbyStand(AIPlayerEntity bot, BlockPos pos) {
        Standability.clearCache();
        BlockPos current = bot.getBlockPos();
        BlockPos exterior = preferredExteriorStand(bot, pos, current);
        if (exterior != null) {
            return exterior;
        }
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        // Search a small shell around the target at practical foot heights. Using only target.y
        // misses the ordinary case where a wall block is one to four blocks above the ground.
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int y = pos.getY() - 4; y <= pos.getY() + 1; y++) {
                        BlockPos candidate = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                        int fromDx = candidate.getX() - current.getX();
                        int fromDz = candidate.getZ() - current.getZ();
                        boolean pathAlreadyConsidersArrived = fromDx * fromDx + fromDz * fromDz <= 1
                                && Math.abs(candidate.getY() - current.getY()) <= 1;
                        if (candidate.equals(current)
                                || pathAlreadyConsidersArrived
                                || candidate.getSquaredDistance(pos) > 100.0D
                                || !isObservableStandable(bot, candidate)) {
                            continue;
                        }
                        double score = candidate.getSquaredDistance(current)
                                + candidate.getSquaredDistance(pos) * 0.05D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate.toImmutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Pick a predictable work position outside the known blueprint bounds. This keeps the bot
     * from slowly walling itself into the structure and gives it a clear angle to the nearest
     * facade without inspecting hidden terrain inside the footprint.
     */
    private BlockPos preferredExteriorStand(AIPlayerEntity bot, BlockPos target, BlockPos current) {
        if (anchor == null) {
            return null;
        }
        int localX = target.getX() - anchor.getX();
        int localZ = target.getZ() - anchor.getZ();
        int left = Math.abs(localX);
        int right = Math.abs(blueprint.width() - 1 - localX);
        int front = Math.abs(localZ);
        int back = Math.abs(blueprint.depth() - 1 - localZ);
        int min = Math.min(Math.min(left, right), Math.min(front, back));

        BlockPos horizontal;
        if (min == left) {
            horizontal = new BlockPos(anchor.getX() - 2, anchor.getY(), target.getZ());
        } else if (min == right) {
            horizontal = new BlockPos(anchor.getX() + blueprint.width() + 1, anchor.getY(), target.getZ());
        } else if (min == front) {
            horizontal = new BlockPos(target.getX(), anchor.getY(), anchor.getZ() - 2);
        } else {
            horizontal = new BlockPos(target.getX(), anchor.getY(), anchor.getZ() + blueprint.depth() + 1);
        }

        for (int delta = 0; delta <= 4; delta++) {
            for (int y : delta == 0
                    ? new int[]{anchor.getY()}
                    : new int[]{anchor.getY() + delta, anchor.getY() - delta}) {
                BlockPos candidate = new BlockPos(horizontal.getX(), y, horizontal.getZ());
                if (!pathAlreadyConsidersArrived(current, candidate)
                        && candidate.getSquaredDistance(target) <= 100.0D
                        && isObservableStandable(bot, candidate)) {
                    return candidate.toImmutable();
                }
            }
        }
        return null;
    }

    /**
     * Work-pose selection is part of planning, not the pathfinder's local collision adapter. In
     * strict survival it therefore has to prove the ground, feet, and head cells observable before
     * consulting raw standability. Operator mode passes these queries through its explicit hidden
     * scan capability.
     */
    private static boolean isObservableStandable(AIPlayerEntity bot, BlockPos candidate) {
        return ObservableWorldQuery.canObserveBlock(bot, candidate.down())
                && ObservableWorldQuery.canObserveCell(bot, candidate)
                && ObservableWorldQuery.canObserveCell(bot, candidate.up())
                && Standability.isStandable(bot.getServerWorld(), candidate);
    }

    private static boolean pathAlreadyConsidersArrived(BlockPos current, BlockPos candidate) {
        int dx = candidate.getX() - current.getX();
        int dz = candidate.getZ() - current.getZ();
        return dx * dx + dz * dz <= 1 && Math.abs(candidate.getY() - current.getY()) <= 1;
    }

    private static String materialName(BlueprintSchema.BlockPlacement placement) {
        return placement.palette() == null || placement.palette().isBlank() ? placement.blockId() : "palette:" + placement.palette();
    }

    private static String compact(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public BlockPos anchor() {
        return anchor == null ? null : anchor.toImmutable();
    }

    public BlueprintSchema blueprint() {
        return blueprint;
    }

    public int placedBlocks() {
        return placedBlocks;
    }

    public int skippedBlocks() {
        return skippedBlocks;
    }

    public static int pseudoPlaceBlocksPerTick() {
        return pseudoPlaceBlocksPerTick;
    }

    public static void setPseudoPlaceBlocksPerTick(int blocksPerTick) {
        pseudoPlaceBlocksPerTick = Math.max(1, Math.min(1024, blocksPerTick));
    }

    public static boolean flattenFillEnabled() {
        return flattenFillEnabled;
    }

    public static void setFlattenFillEnabled(boolean enabled) {
        flattenFillEnabled = enabled;
    }

    public static boolean flattenAirClearEnabled() {
        return flattenAirClearEnabled;
    }

    public static void setFlattenAirClearEnabled(boolean enabled) {
        flattenAirClearEnabled = enabled;
    }

    public void restoreAnchor(BlockPos restoredAnchor) {
        if (restoredAnchor != null && phase == Phase.SITE) {
            this.anchor = restoredAnchor.toImmutable();
            this.note = "restored_anchor=" + compact(this.anchor);
        }
    }

    private record FlattenTarget(BlockPos pos, FlattenKind kind) {
    }
}
