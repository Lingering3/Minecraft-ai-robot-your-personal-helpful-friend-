package io.github.zoyluo.aibot.client;

import io.github.zoyluo.aibot.blueprint.BlueprintTransformer;
import io.github.zoyluo.aibot.task.BlueprintLoader;
import io.github.zoyluo.aibot.task.BlueprintSchema;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.registry.Registries;
import net.minecraft.client.render.VertexRendering;
import java.io.IOException;

/**
 * 蓝图预览渲染(Litematica 式投影):
 * 手持蓝图物品时,始终在世界中渲染蓝图——每个方块用真实方块模型渲染(能看清是什么方块),
 * 外加整体外框。未锁定时跟随准星,锁定后固定,R 键旋转实时反映。
 */
public final class BlueprintPreviewRenderer {
    private BlueprintPreviewRenderer() {
    }

    public static void register() {
        WorldRenderEvents.LAST.register(BlueprintPreviewRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        BlueprintPreviewState state = BlueprintPreviewState.INSTANCE;
        if (!state.active()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        Camera camera = context.camera();
        if (matrices == null || consumers == null || camera == null) {
            return;
        }
        BlockPos anchor = state.currentAnchor();
        if (anchor == null) {
            return;
        }
        BlueprintSchema schema;
        try {
            schema = BlueprintLoader.load(state.blueprintId());
        } catch (IOException | RuntimeException ignored) {
            return;
        }
        BlueprintSchema rotated = BlueprintTransformer.rotate(schema, state.rotation());
        Vec3d cameraPos = camera.getPos();
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager blockRenderManager = client.getBlockRenderManager();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        boolean locked = state.locked();
        float r = locked ? 1.0F : 0.25F;
        float g = locked ? 0.6F : 0.95F;
        float b = locked ? 0.1F : 0.4F;

        // 真实方块模型渲染
        for (BlueprintSchema.BlockPlacement placement : rotated.placements()) {
            BlockState blockState = resolveState(placement.blockId(), placement.stateProperties());
            if (blockState == null || blockState.isAir()) {
                continue;
            }
            int x = anchor.getX() + placement.dx();
            int y = anchor.getY() + placement.dy();
            int z = anchor.getZ() + placement.dz();
            matrices.push();
            matrices.translate(x, y, z);
            blockRenderManager.renderBlockAsEntity(
                    blockState,
                    matrices,
                    consumers,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }

        // 整体外框
        Box bounds = new Box(
                anchor.getX(), anchor.getY(), anchor.getZ(),
                anchor.getX() + rotated.width(),
                anchor.getY() + rotated.height(),
                anchor.getZ() + rotated.depth());
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        VertexRendering.drawBox(matrices, lines, bounds, r, g, b, 1.0F);
        matrices.pop();
    }

    /** 解析 blockId 为 BlockState 并应用状态属性;调色板引用/无法解析时返回 null。 */
    private static BlockState resolveState(String blockId, java.util.Map<String, String> properties) {
        if (blockId == null || blockId.isBlank() || blockId.startsWith("#")) {
            return null;
        }
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            return null;
        }
        Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR && !"minecraft:air".equals(blockId)) {
            return null;
        }
        return io.github.zoyluo.aibot.blueprint.BlockStateProperties.apply(
                block.getDefaultState(), properties);
    }
}
