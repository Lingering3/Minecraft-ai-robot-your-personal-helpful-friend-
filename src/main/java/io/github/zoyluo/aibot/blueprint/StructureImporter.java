package io.github.zoyluo.aibot.blueprint;

import io.github.zoyluo.aibot.task.BlueprintSchema;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Minecraft 结构方块(.nbt structure)蓝图导入。
 * 玩家把结构方块导出的 .nbt 文件放进 游戏目录/blueprints/structures/ 即可被自动识别,
 * 纳入蓝图目录参与打分检索,并可被"蓝图物品"用于伪放置搭建。
 *
 * structure 文件格式(结构方块保存):
 *   size:   [w, h, d]
 *   palette:[{Name:"minecraft:oak_planks", Properties:{...}}, ...]
 *   blocks: [{pos:[x,y,z], state:<palette 索引>}, ...]
 * 空气方块结构文件默认不记录;导入后 placements 仅含实际方块(伪放置不清空未列出的位置)。
 */
public final class StructureImporter {
    private StructureImporter() {
    }

    public static Path structuresDir() {
        return FabricLoader.getInstance().getGameDir().resolve("blueprints").resolve("structures");
    }

    /** 扫描 blueprints/structures/*.nbt,返回 (id=文件名去扩展名) 列表。 */
    public static List<String> listStructures() {
        Path dir = structuresDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.nbt$", ""))
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public static boolean exists(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return Files.exists(structuresDir().resolve(id + ".nbt"));
    }

    public static BlueprintSchema load(String id) throws IOException {
        Path path = structuresDir().resolve(id + ".nbt");
        if (!Files.exists(path)) {
            throw new IOException("structure_not_found: " + id);
        }
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        int[] size = root.getIntArray("size");
        if (size == null || size.length < 3) {
            throw new IOException("structure_bad_size: " + id);
        }
        int width = Math.max(1, size[0]);
        int height = Math.max(1, size[1]);
        int depth = Math.max(1, size[2]);
        NbtList palette = root.getList("palette", NbtElement.COMPOUND_TYPE);
        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        if (palette == null || palette.isEmpty()) {
            throw new IOException("structure_empty_palette: " + id);
        }
        List<String> paletteNames = new ArrayList<>(palette.size());
        for (NbtElement element : palette) {
            NbtCompound entry = (NbtCompound) element;
            String name = entry.getString("Name");
            if (name == null || name.isBlank()) {
                name = "minecraft:air";
            }
            paletteNames.add(name);
        }
        List<BlueprintSchema.BlockPlacement> placements = new ArrayList<>();
        if (blocks != null) {
            for (NbtElement element : blocks) {
                NbtCompound block = (NbtCompound) element;
                int[] pos = block.getIntArray("pos");
                if (pos == null || pos.length < 3) {
                    continue;
                }
                int state = block.getInt("state");
                if (state < 0 || state >= paletteNames.size()) {
                    continue;
                }
                String blockId = paletteNames.get(state);
                if (blockId == null || blockId.isBlank() || "minecraft:air".equals(blockId)) {
                    continue;
                }
                placements.add(new BlueprintSchema.BlockPlacement(
                        pos[0], pos[1], pos[2], blockId, null));
            }
        }
        if (placements.isEmpty()) {
            throw new IOException("structure_empty: " + id);
        }
        return new BlueprintSchema(id, width, height, depth, List.copyOf(placements), List.of());
    }
}
