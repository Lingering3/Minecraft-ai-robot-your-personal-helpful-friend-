package io.github.zoyluo.aibot.blueprint;

import java.util.Map;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

/**
 * 把蓝图里记录的方块状态属性(如楼梯 facing=east、half=top)还原到具体 BlockState。
 * Litematica / Sponge / 结构方块都用 "Name + Properties" 表示方块状态;
 * 导入时逐属性解析字符串并应用,无法识别的属性忽略。
 */
public final class BlockStateProperties {
    private BlockStateProperties() {
    }

    public static BlockState apply(BlockState state, Map<String, String> properties) {
        if (state == null || properties == null || properties.isEmpty()) {
            return state;
        }
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            String raw = properties.get(property.getName());
            if (raw == null) {
                continue;
            }
            Optional<? extends Comparable<?>> parsed = property.parse(raw);
            if (parsed.isPresent()) {
                result = withProperty(result, property, parsed.get());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withProperty(
            BlockState state, Property<T> property, Comparable<?> value) {
        return state.with(property, (T) value);
    }
}
