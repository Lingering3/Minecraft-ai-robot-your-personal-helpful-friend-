package io.github.zoyluo.aibot.network;

import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.MinecraftServer;

public class AINetworkHandler extends ServerPlayNetworkHandler {
    public AINetworkHandler(MinecraftServer server,
                            ClientConnection connection,
                            ServerPlayerEntity player,
                            ConnectedClientData clientData) {
        super(server, connection, player, clientData);
    }

    @Override
    public void sendPacket(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, PacketCallbacks callbacks) {
        if (callbacks != null) {
            callbacks.onSuccess();
        }
    }

    @Override
    public void disconnect(Text reason) {
    }

    @Override
    public void disconnect(DisconnectionInfo disconnectionInfo) {
    }
}
