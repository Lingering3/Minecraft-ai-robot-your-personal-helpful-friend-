package io.github.zoyluo.aibot.mixin;

import io.github.zoyluo.aibot.AIBotMod;
import io.github.zoyluo.aibot.client.BotClientState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererSkinMixin {
    private static final Identifier AIBOT_SKIN =
            Identifier.of(AIBotMod.MOD_ID, "textures/entity/aibot.png");

    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void aibot$rememberTaggedBot(AbstractClientPlayerEntity player,
                                         PlayerEntityRenderState state,
                                         float tickDelta,
                                         org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (player != null && player.getCommandTags().contains("aibot") && state != null) {
            BotClientState.INSTANCE.rememberBot(state.name);
        }
    }

    @Inject(method = "getTexture(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true)
    private void aibot$useLocalBotSkin(PlayerEntityRenderState state,
                                       CallbackInfoReturnable<Identifier> cir) {
        if (state != null && BotClientState.INSTANCE.isKnownBot(state.name)) {
            cir.setReturnValue(AIBOT_SKIN);
        }
    }
}
