package io.github.zoyluo.aibot.action;

import io.github.zoyluo.aibot.entity.AIPlayerEntity;
import io.github.zoyluo.aibot.goal.GoalExecutor;
import io.github.zoyluo.aibot.task.TaskManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * 空闲行为:bot 无任务、无目标、无动作时,平滑转向最近的真实玩家(注视主人)。
 * 每 tick 由 ActionPack.onUpdate 末尾调用。
 */
public final class IdleBehavior {
    private static final double SCAN_RADIUS = 32.0D;
    private static final double SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;
    private static final float MAX_YAW_STEP = 12.0F;
    private static final float MAX_PITCH_STEP = 8.0F;
    private static final int RETARGET_INTERVAL = 20;

    private int retargetCounter;
    private ServerPlayerEntity target;

    public void tick(AIPlayerEntity bot) {
        if (bot.isRemoved() || bot.isDead() || bot.isSpectator()) {
            target = null;
            return;
        }
        if (!isIdle(bot)) {
            target = null;
            retargetCounter = 0;
            return;
        }
        if (retargetCounter-- <= 0 || target == null || target.isRemoved()) {
            target = findNearestPlayer(bot);
            retargetCounter = RETARGET_INTERVAL;
        }
        if (target == null) {
            return;
        }
        lookAt(bot, target);
    }

    private static boolean isIdle(AIPlayerEntity bot) {
        if (bot.getActionPack().hasActiveActions()) {
            return false;
        }
        if (TaskManager.INSTANCE.getActive(bot).isPresent()) {
            return false;
        }
        return !GoalExecutor.INSTANCE.hasActivePlan(bot);
    }

    private static ServerPlayerEntity findNearestPlayer(AIPlayerEntity bot) {
        ServerWorld world = bot.getServerWorld();
        Vec3d eye = bot.getEyePos();
        ServerPlayerEntity nearest = null;
        double nearestSq = SCAN_RADIUS_SQ;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == bot || player.isSpectator() || player.isRemoved()) {
                continue;
            }
            double distSq = player.getEyePos().squaredDistanceTo(eye);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void lookAt(AIPlayerEntity bot, ServerPlayerEntity target) {
        Vec3d botEye = bot.getEyePos();
        Vec3d targetEye = target.getEyePos();
        double dx = targetEye.x - botEye.x;
        double dy = targetEye.y - botEye.y;
        double dz = targetEye.z - botEye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float wantedYaw = (float) Math.toDegrees(-MathHelper.atan2(dx, dz));
        float wantedPitch = (float) Math.toDegrees(-MathHelper.atan2(dy, horizontal));

        float currentYaw = MathHelper.wrapDegrees(bot.getYaw());
        float yawDiff = MathHelper.wrapDegrees(wantedYaw - currentYaw);
        float pitchDiff = wantedPitch - bot.getPitch();
        yawDiff = MathHelper.clamp(yawDiff, -MAX_YAW_STEP, MAX_YAW_STEP);
        pitchDiff = MathHelper.clamp(pitchDiff, -MAX_PITCH_STEP, MAX_PITCH_STEP);

        float newYaw = currentYaw + yawDiff;
        float newPitch = MathHelper.clamp(bot.getPitch() + pitchDiff, -90.0F, 90.0F);
        bot.setYaw(newYaw);
        bot.setPitch(newPitch);
        bot.setHeadYaw(newYaw);
        bot.bodyYaw = newYaw;
    }
}
