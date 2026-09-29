package io.github.zoyluo.aibot.goal;

import io.github.zoyluo.aibot.action.MaterialPalette;
import io.github.zoyluo.aibot.task.BlueprintSchema;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;

public final class StructureVerifier {
    private StructureVerifier() {
    }

    public static StructureReport verify(ServerWorld world,
                                         BlueprintSchema blueprint,
                                         BlockPos anchor,
                                         int placed,
                                         int skipped) {
        if (blueprint == null || anchor == null || blueprint.placements() == null) {
            return new StructureReport("", 0, 0, placed, skipped, 0, 0);
        }
        int matched = 0;
        int mismatched = 0;
        int unsupported = 0;
        for (BlueprintSchema.BlockPlacement placement : blueprint.placements()) {
            if (isUnsupported(placement)) {
                // 原版不存在的方块: 单独归类, 不算缺失失败
                unsupported++;
                continue;
            }
            if (matches(world, anchor, placement)) {
                matched++;
            } else {
                mismatched++;
            }
        }
        return new StructureReport(compact(anchor), blueprint.placements().size(), matched, placed, skipped, mismatched, unsupported);
    }

    /** 判断该 placement 引用的方块在原版注册表中是否存在(air 与 palette 视为支持)。 */
    private static boolean isUnsupported(BlueprintSchema.BlockPlacement placement) {
        if (placement == null || placement.blockId() == null) {
            return true;
        }
        String id = placement.blockId();
        if ("minecraft:air".equals(id)) {
            return false;
        }
        if (placement.palette() != null && !placement.palette().isBlank()) {
            return false;
        }
        try {
            return Registries.BLOCK.getOptionalValue(Identifier.of(id)).isEmpty();
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public static boolean matches(ServerWorld world,
                                  BlockPos anchor,
                                  BlueprintSchema.BlockPlacement placement) {
        if (placement == null || placement.blockId() == null) {
            return false;
        }
        Identifier expectedId;
        try {
            expectedId = Identifier.of(placement.blockId());
        } catch (RuntimeException exception) {
            return false;
        }
        if (world == null || anchor == null) {
            return false;
        }
        BlockPos pos = anchor.add(placement.dx(), placement.dy(), placement.dz());
        var state = world.getBlockState(pos);
        if ("minecraft:air".equals(placement.blockId())) {
            return state.isAir();
        }
        if (placement.palette() != null && !placement.palette().isBlank()) {
            return MaterialPalette.matchesBlock(state, placement.palette())
                    && propertiesMatch(state, placement);
        }
        Block expected = Registries.BLOCK.getOptionalValue(expectedId).orElse(null);
        if (expected == null || !state.isOf(expected)) {
            return false;
        }
        return propertiesMatch(state, expected.getDefaultState(), placement);
    }

    private static boolean propertiesMatch(BlockState actual, BlueprintSchema.BlockPlacement placement) {
        return propertiesMatch(actual, actual, placement);
    }

    /** 若 placement 带状态属性,要求实际状态与还原后的期望状态一致。 */
    private static boolean propertiesMatch(BlockState actual, BlockState defaultExpected,
                                           BlueprintSchema.BlockPlacement placement) {
        if (placement.stateProperties() == null || placement.stateProperties().isEmpty()) {
            return true;
        }
        BlockState expectedState = io.github.zoyluo.aibot.blueprint.BlockStateProperties.apply(
                defaultExpected, placement.stateProperties());
        return actual.equals(expectedState);
    }

    private static String compact(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
