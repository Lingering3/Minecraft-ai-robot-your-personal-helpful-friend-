package io.github.zoyluo.aibot.blueprint;

import io.github.zoyluo.aibot.task.BlueprintSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝图旋转变换(迷你世界蓝图指令语义)。
 * rotation: 0=原方向, 1=顺时针90°, 2=180°, 3=逆时针90°(即270°顺时针)。
 * 仅变换水平面 (dx,dz), 高度 dy 不变;90°/270° 时宽度与深度互换。
 * 用于蓝图物品"右键旋转"后、把旋转后的蓝图交给 BuildTask 落地。
 */
public final class BlueprintTransformer {
    private BlueprintTransformer() {
    }

    public static BlueprintSchema rotate(BlueprintSchema schema, int rotation) {
        int normalized = Math.floorMod(rotation, 4);
        if (normalized == 0) {
            return schema;
        }
        int width = schema.width();
        int depth = schema.depth();
        int newWidth = normalized % 2 == 0 ? width : depth;
        int newDepth = normalized % 2 == 0 ? depth : width;
        List<BlueprintSchema.BlockPlacement> rotated = new ArrayList<>(schema.placements().size());
        for (BlueprintSchema.BlockPlacement placement : schema.placements()) {
            int dx = placement.dx();
            int dz = placement.dz();
            int nx;
            int nz;
            switch (normalized) {
                case 1 -> { nx = dz; nz = depth - 1 - dx; }
                case 2 -> { nx = width - 1 - dx; nz = depth - 1 - dz; }
                default -> { nx = depth - 1 - dz; nz = dx; }
            }
            rotated.add(new BlueprintSchema.BlockPlacement(
                    nx, placement.dy(), nz, placement.blockId(), placement.palette(),
                    placement.stateProperties()));
        }
        return new BlueprintSchema(
                schema.name(), newWidth, schema.height(), newDepth,
                List.copyOf(rotated), List.of());
    }
}
