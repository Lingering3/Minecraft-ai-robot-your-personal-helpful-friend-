package io.github.zoyluo.aibot.network.payload;

import io.github.zoyluo.aibot.AIBotMod;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * 客户端"蓝图物品中键确认搭建"请求。
 * 携带: 蓝图 id、锚点坐标(蓝图局部 (0,0,0) 对应的世界方块)、旋转(0-3)。
 * 服务端校验创造模式与手持物品后,派发 BuildTask(伪放置,无视地形直接替换方块)。
 */
public record BlueprintBuildC2S(
        String blueprintId,
        int anchorX,
        int anchorY,
        int anchorZ,
        int rotation
) implements CustomPayload {
    public static final Id<BlueprintBuildC2S> ID = new Id<>(Identifier.of(AIBotMod.MOD_ID, "blueprint_build"));
    public static final PacketCodec<RegistryByteBuf, BlueprintBuildC2S> CODEC =
            PacketCodec.of(BlueprintBuildC2S::write, BlueprintBuildC2S::new);

    private BlueprintBuildC2S(RegistryByteBuf buf) {
        this(buf.readString(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(blueprintId);
        buf.writeVarInt(anchorX);
        buf.writeVarInt(anchorY);
        buf.writeVarInt(anchorZ);
        buf.writeVarInt(rotation);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
