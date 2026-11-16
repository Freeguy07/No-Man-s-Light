package com.freeguy07.nomanslight.entity.ai;

import com.freeguy07.nomanslight.torch.TorchExtinguishing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class ExtinguishTorchGoal extends Goal {
    private static final int SEARCH_RADIUS = 12;
    private static final int VERTICAL_SEARCH_RANGE = 4;
    private static final double MANUAL_REACH_HORIZONTAL_SQ = 2.4 * 2.4;
    private static final double POTION_MIN_DIST_SQ = 3.2 * 3.2;
    private static final double POTION_MAX_DIST_SQ = 10.0 * 10.0;
    private static final int MANUAL_EXTINGUISH_DELAY = 12;
    private static final int POTION_THROW_DELAY = 20;
    private static final int POST_THROW_WAIT = 20;
    private static final int MAX_TRY_TICKS = 240;

    private final PathfinderMob mob;
    private final double speedModifier;
    private final boolean allowPotionThrow;

    @Nullable
    private BlockPos targetPos;
    @Nullable
    private BlockPos moveTargetPos;

    private int nextStartTick;
    private int tryTicks;
    private int actionTicks;
    private int postThrowWaitTicks;
    private int potionCooldownTicks;
    private boolean usePotion;

    public ExtinguishTorchGoal(PathfinderMob mob, double speedModifier, boolean allowPotionThrow) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.allowPotionThrow = allowPotionThrow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.potionCooldownTicks > 0) {
            this.potionCooldownTicks--;
        }
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        }
        this.nextStartTick = reducedTickDelay(100 + this.mob.getRandom().nextInt(200));

        if (!canRunCommonChecks()) {
            return false;
        }

        BlockPos found = findNearestTarget();
        if (found == null) {
            return false;
        }

        this.targetPos = found;
        this.moveTargetPos = findWalkableApproachPos(found);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetPos == null || this.tryTicks >= MAX_TRY_TICKS) {
            return false;
        }
        if (!canRunCommonChecks()) {
            return false;
        }
        BlockState state = this.mob.level().getBlockState(this.targetPos);
        return TorchExtinguishing.isTargetable(state);
    }

    @Override
    public void start() {
        this.tryTicks = 0;
        this.actionTicks = 0;
        this.postThrowWaitTicks = 0;
        this.usePotion = shouldStartWithPotion();
        if (!this.usePotion) {
            moveToTarget();
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.moveTargetPos = null;
        this.usePotion = false;
        this.actionTicks = 0;
        this.postThrowWaitTicks = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        this.tryTicks++;
        if (this.potionCooldownTicks > 0) {
            this.potionCooldownTicks--;
        }

        Vec3 targetCenter = Vec3.atCenterOf(this.targetPos);

        if (this.postThrowWaitTicks > 0) {
            this.postThrowWaitTicks--;
            this.mob.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z);
            if (this.postThrowWaitTicks == 0) {
                // Potion landed and target is still lit; walk in to finish manually
                this.usePotion = false;
                this.actionTicks = 0;
                moveToTarget();
            }
            return;
        }

        if (this.usePotion) {
            tickPotionThrow(targetCenter);
        } else {
            tickManualExtinguish(targetCenter);
        }
    }

    private void tickPotionThrow(Vec3 targetCenter) {
        if (this.targetPos == null) {
            return;
        }

        double distSq = this.mob.distanceToSqr(targetCenter);
        boolean inRange = distSq <= POTION_MAX_DIST_SQ;
        boolean hasLos = hasLineOfSightToBlock(this.targetPos);

        if (inRange && hasLos) {
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z);
            this.actionTicks++;

            if (this.actionTicks >= POTION_THROW_DELAY) {
                throwWaterPotion(this.targetPos);
                this.actionTicks = 0;
                this.potionCooldownTicks = 60;
                this.postThrowWaitTicks = POST_THROW_WAIT;
            }
        } else {
            this.actionTicks = 0;
            if (this.tryTicks % 20 == 0 || this.mob.getNavigation().isDone()) {
                moveToTarget();
            }
            // If we ended up right next to the torch while closing distance, extinguish manually
            if (isWithinManualReach(this.targetPos) && hasLos) {
                this.usePotion = false;
            }
        }
    }

    private void tickManualExtinguish(Vec3 targetCenter) {
        if (this.targetPos == null) {
            return;
        }

        if (isWithinManualReach(this.targetPos) && hasLineOfSightToBlock(this.targetPos)) {
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z);
            this.actionTicks++;

            if (this.actionTicks >= MANUAL_EXTINGUISH_DELAY) {
                if (this.mob.level() instanceof ServerLevel serverLevel) {
                    BlockState state = serverLevel.getBlockState(this.targetPos);
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    TorchExtinguishing.extinguish(serverLevel, this.targetPos, state, this.mob);
                }
                this.targetPos = null;
            }
        } else {
            this.actionTicks = 0;
            if (this.tryTicks % 20 == 0 || this.mob.getNavigation().isDone()) {
                moveToTarget();
            }
        }
    }

    private boolean canRunCommonChecks() {
        if (this.mob.getTarget() != null) {
            return false;
        }
        if (this.mob instanceof Witch witch && witch.isDrinkingPotion()) {
            return false;
        }
        return EventHooks.canEntityGrief(this.mob.level(), this.mob);
    }

    private boolean shouldStartWithPotion() {
        if (!this.allowPotionThrow || !(this.mob instanceof Witch) || this.targetPos == null) {
            return false;
        }
        if (this.potionCooldownTicks > 0) {
            return false;
        }
        double distSq = this.mob.distanceToSqr(Vec3.atCenterOf(this.targetPos));
        boolean outOfManualVerticalReach = this.targetPos.getY() - this.mob.getY() > 2.5;
        if (outOfManualVerticalReach && distSq <= POTION_MAX_DIST_SQ) {
            return true;
        }
        return distSq >= POTION_MIN_DIST_SQ
                && distSq <= POTION_MAX_DIST_SQ
                && hasLineOfSightToBlock(this.targetPos)
                && this.mob.getRandom().nextFloat() < 0.65F;
    }

    private void throwWaterPotion(BlockPos pos) {
        Level level = this.mob.level();
        ThrownPotion potion = new ThrownPotion(level, this.mob);
        potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));

        double dx = (pos.getX() + 0.5) - this.mob.getX();
        double dy = (pos.getY() + 0.25) - potion.getY();
        double dz = (pos.getZ() + 0.5) - this.mob.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        potion.shoot(dx, dy + horizontalDist * 0.2, dz, 0.75F, 2.0F);
        if (!this.mob.isSilent()) {
            level.playSound(
                    null,
                    this.mob.getX(),
                    this.mob.getY(),
                    this.mob.getZ(),
                    SoundEvents.WITCH_THROW,
                    this.mob.getSoundSource(),
                    1.0F,
                    0.8F + this.mob.getRandom().nextFloat() * 0.4F
            );
        }
        this.mob.swing(InteractionHand.MAIN_HAND);
        level.addFreshEntity(potion);
    }

    private void moveToTarget() {
        BlockPos dest = this.moveTargetPos != null ? this.moveTargetPos : this.targetPos;
        if (dest != null) {
            this.mob.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, this.speedModifier);
        }
    }

    private boolean isWithinManualReach(BlockPos pos) {
        double dx = (pos.getX() + 0.5) - this.mob.getX();
        double dz = (pos.getZ() + 0.5) - this.mob.getZ();
        double dy = pos.getY() - this.mob.getY();
        return (dx * dx + dz * dz) <= MANUAL_REACH_HORIZONTAL_SQ && dy >= -1.5 && dy <= 2.6;
    }

    private boolean hasLineOfSightToBlock(BlockPos pos) {
        Vec3 eyePos = this.mob.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(pos);
        BlockHitResult hit = this.mob.level().clip(new ClipContext(
                eyePos,
                targetCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this.mob
        ));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    @Nullable
    private BlockPos findNearestTarget() {
        BlockPos origin = this.mob.blockPosition();
        Level level = this.mob.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dy = -VERTICAL_SEARCH_RANGE; dy <= VERTICAL_SEARCH_RANGE; dy++) {
            int y = origin.getY() + dy;
            if (level.isOutsideBuildHeight(y)) {
                continue;
            }
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, y, origin.getZ() + dz);
                    double distSq = cursor.distSqr(origin);
                    if (distSq >= bestDistSq) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (TorchExtinguishing.isTargetable(state)) {
                        bestDistSq = distSq;
                        bestPos = cursor.immutable();
                    }
                }
            }
        }
        return bestPos;
    }

    private BlockPos findWalkableApproachPos(BlockPos torchPos) {
        Level level = this.mob.level();
        // Check directly underneath the torch (handles floor torches and wall torches up to 2 blocks off the floor)
        for (int down = 0; down <= 2; down++) {
            BlockPos candidate = torchPos.below(down);
            if (isStandable(level, candidate)) {
                return candidate;
            }
        }
        // Check adjacent columns in case the torch sits on a fence/pillar
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = torchPos.relative(dir);
            for (int down = 0; down <= 2; down++) {
                BlockPos candidate = side.below(down);
                if (isStandable(level, candidate)) {
                    return candidate;
                }
            }
        }
        return torchPos;
    }

    private static boolean isStandable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
