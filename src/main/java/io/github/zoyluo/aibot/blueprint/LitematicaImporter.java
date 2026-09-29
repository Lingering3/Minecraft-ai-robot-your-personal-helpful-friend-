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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Litematica(.litematic)蓝图导入。
 * 玩家把 Litematica 导出的 .litematic 文件放进 游戏目录/blueprints/structures/ 即可被自动识别,
 * 纳入蓝图目录参与打分检索,并可被"蓝图物品"用于伪放置搭建。
 *
 * 文件结构(gzip NBT):
 *   Regions: { <区域名>: {
 *     Size: {x,y,z}(可负), Position: {x,y,z},
 *     BlockStatePalette: [{Name, Properties:{...}}, ...],
 *     BlockStates: long[](位打包的调色板索引)
 *   }}
 * 位解包复用 LitematicaBitArray;轴顺序 index=(iy*nz+iz)*nx+ix。
 */
public final class LitematicaImporter {
    private LitematicaImporter() {
    }

    public static Path structuresDir() {
        return FabricLoader.getInstance().getGameDir().resolve("blueprints").resolve("structures");
    }

    private static List<Path> searchDirs() {
        List<Path> dirs = new ArrayList<>();
        dirs.add(structuresDir());
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            dirs.add(Path.of(home, "Downloads"));
        }
        return dirs;
    }

    /** 扫描 blueprints/structures/*.litematic 与本机 Downloads/*.litematic,返回 id 列表。 */
    public static List<String> listLitematics() {
        Map<String, Path> ids = new LinkedHashMap<>();
        for (Path dir : searchDirs()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream
                        .filter(path -> path.getFileName().toString().endsWith(".litematic"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(path -> ids.putIfAbsent(
                                path.getFileName().toString().replaceFirst("\\.litematic$", ""),
                                path));
            } catch (IOException ignored) {
                // 忽略单个来源目录失败,其他目录仍可用。
            }
        }
        return ids.keySet().stream().sorted().toList();
    }

    public static boolean exists(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return findLitematic(id).isPresent();
    }

    public static BlueprintSchema load(String id) throws IOException {
        Path path = findLitematic(id).orElse(null);
        if (path == null) {
            throw new IOException("litematic_not_found: " + id);
        }
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound regions = root.getCompound("Regions");
        if (regions == null || regions.getKeys().isEmpty()) {
            throw new IOException("litematic_no_regions: " + id);
        }

        // 收集所有区域的方块(相对 schematic 原点的世界坐标)
        List<RawBlock> rawBlocks = new ArrayList<>();
        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName);
            collectRegion(region, rawBlocks);
        }
        if (rawBlocks.isEmpty()) {
            throw new IOException("litematic_empty: " + id);
        }

        // 归一化:包围盒平移到 (0,0,0)
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RawBlock block : rawBlocks) {
            minX = Math.min(minX, block.x);
            minY = Math.min(minY, block.y);
            minZ = Math.min(minZ, block.z);
            maxX = Math.max(maxX, block.x);
            maxY = Math.max(maxY, block.y);
            maxZ = Math.max(maxZ, block.z);
        }
        List<BlueprintSchema.BlockPlacement> placements = new ArrayList<>(rawBlocks.size());
        for (RawBlock block : rawBlocks) {
            placements.add(new BlueprintSchema.BlockPlacement(
                    block.x - minX,
                    block.y - minY,
                    block.z - minZ,
                    block.blockId,
                    null,
                    block.properties));
        }
        placements.sort(Comparator
                .comparingInt(BlueprintSchema.BlockPlacement::dy)
                .thenComparingInt(LitematicaImporter::placementPriority)
                .thenComparingInt(BlueprintSchema.BlockPlacement::dx)
                .thenComparingInt(BlueprintSchema.BlockPlacement::dz));
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        return new BlueprintSchema(id, width, height, depth, List.copyOf(placements), List.of());
    }

    private static int placementPriority(BlueprintSchema.BlockPlacement placement) {
        String blockId = placement.blockId();
        if (blockId == null || blockId.isBlank() || "minecraft:air".equals(blockId)) {
            return 3;
        }
        if (isAttachmentBlock(blockId)) {
            return 2;
        }
        if (isGravityOrFluidSensitiveBlock(blockId)) {
            return 1;
        }
        return 0;
    }

    private static boolean isAttachmentBlock(String blockId) {
        return blockId.endsWith("_button")
                || blockId.endsWith("_pressure_plate")
                || blockId.endsWith("_sign")
                || blockId.endsWith("_wall_sign")
                || blockId.endsWith("_hanging_sign")
                || blockId.endsWith("_wall_hanging_sign")
                || blockId.endsWith("_torch")
                || blockId.endsWith("_wall_torch")
                || blockId.endsWith("_banner")
                || blockId.endsWith("_wall_banner")
                || blockId.endsWith("_door")
                || blockId.endsWith("_trapdoor")
                || blockId.endsWith("_rail")
                || blockId.endsWith("_coral")
                || blockId.endsWith("_coral_fan")
                || blockId.endsWith("_coral_wall_fan")
                || blockId.contains("redstone")
                || blockId.contains("tripwire")
                || blockId.contains("lever")
                || blockId.contains("ladder")
                || blockId.contains("vine")
                || blockId.contains("painting");
    }

    private static boolean isGravityOrFluidSensitiveBlock(String blockId) {
        return blockId.contains("sand")
                || blockId.contains("gravel")
                || blockId.contains("concrete_powder")
                || blockId.contains("anvil")
                || blockId.contains("scaffolding")
                || blockId.endsWith("_carpet")
                || blockId.endsWith("_bed")
                || blockId.endsWith("_cake")
                || blockId.endsWith("_pot")
                || blockId.endsWith("_flower")
                || blockId.endsWith("_sapling")
                || blockId.endsWith("_mushroom")
                || blockId.endsWith("_plant")
                || blockId.endsWith("_crop")
                || blockId.endsWith("_stem");
    }

    private record RawBlock(int x, int y, int z, String blockId, Map<String, String> properties) {
    }

    private static Optional<Path> findLitematic(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (Path dir : searchDirs()) {
            Path path = dir.resolve(id + ".litematic");
            if (Files.exists(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private static void collectRegion(NbtCompound region, List<RawBlock> out) throws IOException {
        NbtCompound sizeNbt = region.getCompound("Size");
        NbtCompound positionNbt = region.getCompound("Position");
        if (sizeNbt == null || positionNbt == null) {
            throw new IOException("litematic_bad_region");
        }
        int sx = sizeNbt.getInt("x");
        int sy = sizeNbt.getInt("y");
        int sz = sizeNbt.getInt("z");
        if (sx == 0 || sy == 0 || sz == 0) {
            throw new IOException("litematic_bad_size");
        }
        int ax = positionNbt.getInt("x");
        int ay = positionNbt.getInt("y");
        int az = positionNbt.getInt("z");
        int nx = Math.abs(sx);
        int ny = Math.abs(sy);
        int nz = Math.abs(sz);

        NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
        if (palette == null || palette.isEmpty()) {
            return;
        }
        // 预解析调色板
        List<PaletteEntry> entries = new ArrayList<>(palette.size());
        for (NbtElement element : palette) {
            NbtCompound stateNbt = (NbtCompound) element;
            String name = stateNbt.getString("Name");
            if (name == null || name.isBlank()) {
                name = "minecraft:air";
            }
            Map<String, String> properties = new HashMap<>();
            NbtCompound propertiesNbt = stateNbt.getCompound("Properties");
            if (propertiesNbt != null) {
                for (String key : propertiesNbt.getKeys()) {
                    properties.put(key, propertiesNbt.getString(key));
                }
            }
            entries.add(new PaletteEntry(name, properties));
        }

        long[] blockStates = region.getLongArray("BlockStates");
        long total = (long) nx * ny * nz;
        int bitsPerEntry = Math.max(2, (int) Math.ceil(log2(entries.size())));
        LitematicaBitArray bitArray = new LitematicaBitArray(bitsPerEntry, total, blockStates);

        for (int iy = 0; iy < ny; iy++) {
            for (int iz = 0; iz < nz; iz++) {
                for (int ix = 0; ix < nx; ix++) {
                    long index = ((long) iy * nz + iz) * nx + ix;
                    int paletteIndex = bitArray.getAt(index);
                    if (paletteIndex < 0 || paletteIndex >= entries.size()) {
                        continue;
                    }
                    PaletteEntry entry = entries.get(paletteIndex);
                    if ("minecraft:air".equals(entry.name)) {
                        continue;
                    }
                    int wx = ax + (sx < 0 ? -ix : ix);
                    int wy = ay + (sy < 0 ? -iy : iy);
                    int wz = az + (sz < 0 ? -iz : iz);
                    out.add(new RawBlock(wx, wy, wz, entry.name,
                            entry.properties.isEmpty() ? Map.of() : Map.copyOf(entry.properties)));
                }
            }
        }
    }

    private record PaletteEntry(String name, Map<String, String> properties) {
    }

    private static double log2(int value) {
        return Math.log(Math.max(1, value)) / Math.log(2);
    }
}
