package io.github.zoyluo.aibot.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerEntityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the server's complete section cache for collision checks during HIDDEN-section ticks. */
@Mixin(ServerWorld.class)
public interface ServerWorldEntityManagerAccessorMixin {
    @Accessor("entityManager")
    ServerEntityManager<Entity> aibot$getEntityManager();
}
