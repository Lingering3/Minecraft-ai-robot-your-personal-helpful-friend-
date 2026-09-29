package io.github.zoyluo.aibot.blueprint;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置蓝图释放:把打包进 jar 的 12 个 .litematic 建筑,
 * 在首次启动时释放到 blueprints/structures/,随后被现有扫描/加载逻辑自动识别。
 * 已存在的同名文件不覆盖(允许玩家修改或删除)。
 * 同时提供每个内置蓝图的中文显示名与语义关键词,供目录检索与 StepFun 推荐使用。
 */
public final class BuiltinBlueprints {
    private BuiltinBlueprints() {
    }

    public record Metadata(String displayName, List<String> tags) {
    }

    public static final Map<String, Metadata> METADATA;

    static {
        Map<String, Metadata> map = new LinkedHashMap<>();
        map.put("medieval_cottage", new Metadata("中世纪小屋",
                List.of("house", "cottage", "medieval", "中世纪", "小屋", "房子", "村舍", "木屋")));
        map.put("small_castle", new Metadata("小城堡",
                List.of("castle", "fortress", "medieval", "城堡", "堡垒", "城池", "王城")));
        map.put("watchtower", new Metadata("瞭望塔",
                List.of("tower", "watchtower", "guard", "瞭望塔", "哨塔", "高塔", "塔")));
        map.put("windmill", new Metadata("风车",
                List.of("windmill", "mill", "风车", "磨坊")));
        map.put("lighthouse", new Metadata("灯塔",
                List.of("lighthouse", "beacon", "灯塔", "航标", "灯塔")));
        map.put("japanese_house", new Metadata("日式小屋",
                List.of("house", "japanese", "asian", "日式", "和风", "小屋", "鸟居", "亚洲")));
        map.put("stone_bridge", new Metadata("石拱桥",
                List.of("bridge", "stone", "crossing", "石桥", "桥", "拱桥", "过河", "桥梁")));
        map.put("modern_villa", new Metadata("现代别墅",
                List.of("house", "villa", "modern", "别墅", "现代", "豪宅", "洋房", "泳池")));
        map.put("treehouse", new Metadata("树屋",
                List.of("house", "tree", "treehouse", "树屋", "树上", "木屋")));
        map.put("fountain", new Metadata("喷泉",
                List.of("fountain", "water", "decoration", "喷泉", "水景", "装饰", "水池")));
        map.put("barn", new Metadata("谷仓",
                List.of("barn", "farm", "warehouse", "谷仓", "仓库", "农场", "农舍")));
        map.put("market_stall", new Metadata("市场摊位",
                List.of("market", "stall", "shop", "摊位", "市场", "集市", "商铺", "小卖部")));
        METADATA = Map.copyOf(map);
    }

    public static final List<String> IDS = List.copyOf(METADATA.keySet());

    public static void extractIfMissing() {
        Path dir = LitematicaImporter.structuresDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            return;
        }
        for (String id : IDS) {
            Path target = dir.resolve(id + ".litematic");
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream in = BuiltinBlueprints.class.getResourceAsStream(
                    "/data/aibot/builtin_blueprints/" + id + ".litematic")) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, target);
            } catch (Exception ignored) {
            }
        }
    }
}
