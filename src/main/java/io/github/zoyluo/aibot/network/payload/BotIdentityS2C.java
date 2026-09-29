package io.github.zoyluo.aibot.network.payload;

import io.github.zoyluo.aibot.AIBotMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BotIdentityS2C(String botName) implements CustomPayload {
    public static final Id<BotIdentityS2C> ID = new Id<>(Identifier.of(AIBotMod.MOD_ID, "bot_identity"));
    public static final PacketCodec<RegistryByteBuf, BotIdentityS2C> CODEC =
            PacketCodec.of(BotIdentityS2C::write, BotIdentityS2C::new);

    private BotIdentityS2C(RegistryByteBuf buf) {
        this(buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(botName);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
