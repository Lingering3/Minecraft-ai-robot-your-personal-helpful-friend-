package io.github.zoyluo.aibot.item;

import io.github.zoyluo.aibot.blueprint.BlueprintCatalog;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.world.World;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.DataComponentTypes;

/**
 * 蓝图物品(迷你世界蓝图指令式交互):
 * 手持时客户端渲染蓝图预览;右键旋转方向;中键第一次锁定位置、第二次确认搭建。
 * 物品 NBT 只存蓝图 id("aibot_blueprint"),预览位置与旋转由客户端状态管理,
 * 确认搭建时通过 BlueprintBuildC2S 把 位置+旋转 发给服务端落地。
 */
public final class BlueprintItem extends Item {
    public static final String NBT_KEY = "aibot_blueprint";

    public BlueprintItem(Item.Settings settings) {
        super(settings);
    }

    public static String blueprintId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlueprintItem)) {
            return null;
        }
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return null;
        }
        NbtCompound nbt = component.copyNbt();
        return nbt == null ? null : nbt.getString(NBT_KEY);
    }

    public static ItemStack withBlueprint(ItemStack stack, String blueprintId) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component != null ? component.copyNbt() : new NbtCompound();
        nbt.putString(NBT_KEY, blueprintId);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(BlueprintCatalog.chineseName(blueprintId) + "（" + blueprintId + "）"));
        return stack;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        // 旋转与确认均由客户端按键处理,服务端不消耗也不做默认交互(避免播放使用动画)。
        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        String id = blueprintId(stack);
        if (id != null && !id.isBlank()) {
            tooltip.add(Text.literal("蓝图: " + BlueprintCatalog.chineseName(id) + "（" + id + "）"));
        }
        tooltip.add(Text.literal("中键/G 键: 锁定位置 · R 键: 旋转 · 再次中键/G 键: 开始搭建"));
    }
}
