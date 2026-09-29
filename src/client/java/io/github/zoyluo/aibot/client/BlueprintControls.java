package io.github.zoyluo.aibot.client;

import io.github.zoyluo.aibot.item.BlueprintItem;
import io.github.zoyluo.aibot.network.payload.BlueprintBuildC2S;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.text.Text;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 蓝图物品交互控制(Litematica 式投影,不依赖游戏右键):
 * 手持蓝图物品时,投影跟随准星实时显示;
 * R 键 = 旋转 90°;中键(或 G 键)第一次 = 锁定位置;第二次 = 确认搭建(发 C2S)。
 * 同时提供中键与 G 键两个确认键,避免中键与游戏内置选取方块冲突导致无响应。
 */
public final class BlueprintControls {
    private static final double RAYCAST_DISTANCE = 8.0D;

    private static KeyBinding confirmKey;
    private static KeyBinding confirmKeyKeyboard;
    private static KeyBinding rotateKey;

    private BlueprintControls() {
    }

    public static void register() {
        confirmKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aibot.blueprint_confirm",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                "key.categories.aibot"));
        confirmKeyKeyboard = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aibot.blueprint_confirm_keyboard",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.aibot"));
        rotateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aibot.blueprint_rotate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.aibot"));
        ClientTickEvents.END_CLIENT_TICK.register(BlueprintControls::onTick);
    }

    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        String blueprintId = heldBlueprintId(client);
        BlueprintPreviewState state = BlueprintPreviewState.INSTANCE;
        if (blueprintId == null) {
            state.clear();
            return;
        }
        if (!blueprintId.equals(state.blueprintId())) {
            state.begin(blueprintId);
        }
        if (!state.locked()) {
            state.updateCandidate(currentAnchor(client));
        }
        while (rotateKey.wasPressed()) {
            state.rotate();
        }
        // 中键与 G 键都可确认
        boolean confirmPressed = false;
        while (confirmKey.wasPressed()) {
            confirmPressed = true;
        }
        while (confirmKeyKeyboard.wasPressed()) {
            confirmPressed = true;
        }
        if (confirmPressed) {
            if (state.locked()) {
                confirmAndSend(client, state);
            } else {
                BlockPos anchor = state.currentAnchor();
                if (anchor != null) {
                    state.lock(anchor);
                    client.player.sendMessage(Text.literal(
                            "[蓝图] 位置已锁定 (" + anchor.toShortString() + "),按 R 旋转,再次按中键或 G 键开始搭建。"), false);
                }
            }
        }
    }

    private static String heldBlueprintId(MinecraftClient client) {
        ItemStack main = client.player.getMainHandStack();
        String id = BlueprintItem.blueprintId(main);
        if (id != null && !id.isBlank()) {
            return id;
        }
        ItemStack off = client.player.getOffHandStack();
        return BlueprintItem.blueprintId(off);
    }

    /**
     * 候选锚点:准星射线命中的方块(蓝图 y=0 放在该方块);
     * 未命中则取玩家前方 5 格、同高度。
     */
    private static BlockPos currentAnchor(MinecraftClient client) {
        HitResult hit = client.player.raycast(RAYCAST_DISTANCE, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
        }
        Direction facing = client.player.getHorizontalFacing();
        return client.player.getBlockPos().offset(facing, 5);
    }

    private static void confirmAndSend(MinecraftClient client, BlueprintPreviewState state) {
        BlockPos anchor = state.anchor();
        if (anchor == null) {
            return;
        }
        ClientPlayNetworking.send(new BlueprintBuildC2S(
                state.blueprintId(),
                anchor.getX(),
                anchor.getY(),
                anchor.getZ(),
                state.rotation()));
        client.player.sendMessage(Text.literal("[蓝图] 已确认,正在请求搭建…"), false);
        state.resetAfterConfirm();
    }
}
