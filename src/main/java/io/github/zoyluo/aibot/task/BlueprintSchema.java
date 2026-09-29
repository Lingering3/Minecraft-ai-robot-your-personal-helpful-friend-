package io.github.zoyluo.aibot.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record BlueprintSchema(
        String name,
        int width,
        int height,
        int depth,
        List<BlockPlacement> placements,
        List<Op> ops
) {
    public record BlockPlacement(int dx, int dy, int dz, String blockId, String palette,
                                 Map<String, String> stateProperties) {
        public BlockPlacement(int dx, int dy, int dz, String blockId) {
            this(dx, dy, dz, blockId, null, Map.of());
        }

        public BlockPlacement(int dx, int dy, int dz, String blockId, String palette) {
            this(dx, dy, dz, blockId, palette, Map.of());
        }
    }

    public record Op(String type, int[] from, int[] to, String block, String palette) {
    }

    public static BlueprintSchema hut5x5() {
        List<BlockPlacement> blocks = new ArrayList<>();
        String plank = "minecraft:oak_planks";
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new BlockPlacement(x, 0, z, plank));
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                blocks.add(new BlockPlacement(x, y, 0, plank));
                blocks.add(new BlockPlacement(x, y, 4, plank));
            }
            for (int z = 1; z < 4; z++) {
                blocks.add(new BlockPlacement(0, y, z, plank));
                blocks.add(new BlockPlacement(4, y, z, plank));
            }
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new BlockPlacement(x, 4, z, plank));
            }
        }
        blocks.removeIf(block -> block.dx() == 2 && block.dz() == 0 && (block.dy() == 1 || block.dy() == 2));
        return new BlueprintSchema("hut_5x5", 5, 5, 5, List.copyOf(blocks), List.of());
    }

    public static BlueprintSchema smallHutOps() {
        return new BlueprintSchema("small_hut", 5, 5, 5, List.of(
                new BlockPlacement(2, 1, 0, "minecraft:air"),
                new BlockPlacement(2, 2, 0, "minecraft:air")
        ), List.of(
                new Op("layer", new int[]{0, 0, 0}, new int[]{4, 0, 4}, null, "planks"),
                new Op("hollow_box", new int[]{0, 1, 0}, new int[]{4, 3, 4}, null, "planks"),
                new Op("layer", new int[]{0, 4, 0}, new int[]{4, 4, 4}, null, "planks")
        ));
    }

    public static BlueprintSchema glassCabin() {
        return new BlueprintSchema("glass_cabin", 7, 5, 7, List.of(
                new BlockPlacement(3, 1, 0, "minecraft:air"),
                new BlockPlacement(3, 2, 0, "minecraft:air")
        ), List.of(
                new Op("layer", new int[]{0, 0, 0}, new int[]{6, 0, 6}, null, "stone_like"),
                new Op("hollow_box", new int[]{0, 1, 0}, new int[]{6, 3, 6}, null, "glass"),
                new Op("layer", new int[]{0, 4, 0}, new int[]{6, 4, 6}, null, "planks")
        ));
    }

    public static BlueprintSchema watchTower() {
        return new BlueprintSchema("watch_tower", 5, 9, 5, List.of(
                new BlockPlacement(2, 1, 0, "minecraft:air"),
                new BlockPlacement(2, 2, 0, "minecraft:air")
        ), List.of(
                new Op("layer", new int[]{0, 0, 0}, new int[]{4, 0, 4}, null, "stone_like"),
                new Op("hollow_box", new int[]{1, 1, 1}, new int[]{3, 6, 3}, null, "stone_like"),
                new Op("layer", new int[]{0, 7, 0}, new int[]{4, 7, 4}, null, "planks"),
                new Op("hollow_box", new int[]{0, 8, 0}, new int[]{4, 8, 4}, null, "planks")
        ));
    }

    public static BlueprintSchema simpleBridge() {
        return new BlueprintSchema("simple_bridge", 9, 3, 3, List.of(), List.of(
                new Op("layer", new int[]{0, 0, 0}, new int[]{8, 0, 2}, null, "planks"),
                new Op("layer", new int[]{0, 1, 0}, new int[]{8, 1, 0}, null, "logs"),
                new Op("layer", new int[]{0, 1, 2}, new int[]{8, 1, 2}, null, "logs"),
                new Op("layer", new int[]{0, 2, 0}, new int[]{8, 2, 0}, null, "planks"),
                new Op("layer", new int[]{0, 2, 2}, new int[]{8, 2, 2}, null, "planks")
        ));
    }

    /**
     * P3 参数化房屋:按 "custom:宽x深x高:材质" 规格生成(如 custom:7x5x4:stone)。
     * 结构与 small_hut 同款:地板 + 空心墙体 + 平屋顶 + 正面居中 2 高门洞。
     * 宽/深=外径含墙(钳制 3..16 防巨构耗尽材料),高=墙体净高(钳制 2..8;总高=高+地板1+屋顶1)。
     * 材质=palette 名(planks/stone_like/glass…见 MaterialPalette;未知按 planks),备料/建造接受家族任意成员。
     * 规格非法返回 null(调用方报 IOException)。
     */
    public static BlueprintSchema parametricHouse(String spec) {
        try {
            String[] parts = spec.split(":");
            if (parts.length < 2) {
                return null;
            }
            String[] dims = parts[1].split("x");
            if (dims.length != 3) {
                return null;
            }
            int w = Math.max(3, Math.min(16, Integer.parseInt(dims[0].trim())));
            int d = Math.max(3, Math.min(16, Integer.parseInt(dims[1].trim())));
            int h = Math.max(2, Math.min(8, Integer.parseInt(dims[2].trim())));
            String palette = parts.length >= 3 && !parts[2].isBlank() ? parts[2].trim() : "planks";
            int doorX = w / 2; // 正面(z=0)居中门洞,2 格高
            List<BlockPlacement> door = List.of(
                    new BlockPlacement(doorX, 1, 0, "minecraft:air"),
                    new BlockPlacement(doorX, 2, 0, "minecraft:air"));
            List<Op> ops = List.of(
                    new Op("layer", new int[]{0, 0, 0}, new int[]{w - 1, 0, d - 1}, null, palette),
                    new Op("hollow_box", new int[]{0, 1, 0}, new int[]{w - 1, h, d - 1}, null, palette),
                    new Op("layer", new int[]{0, h + 1, 0}, new int[]{w - 1, h + 1, d - 1}, null, palette));
            return new BlueprintSchema(spec, w, h + 2, d, door, ops);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
