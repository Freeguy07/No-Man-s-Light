package com.freeguy07.nomanslight.entity.ai;

import com.freeguy07.nomanslight.entity.MobWhitelist;
import com.freeguy07.nomanslight.torch.TorchExtinguishing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class ExtinguishTorchGoal extends Goal {
    private static final int SEARCH_RADIUS = 12;
    private static final int VERTICAL_SEARCH_RANGE = 4;
    private static final int MANUAL_SWING_TICK = 3;
    private static final int MANUAL_EXTINGUISH_DELAY = 5;
    private static final int MAX_TRY_TICKS = 240;
    private static final int MAX_STUCK_TICKS = 30;
    private static final int FAILED_TARGET_COOLDOWN = 400;

    private final PathfinderMob mob;
    private final double speedModifier;

    @Nullable
    private BlockPos targetPos;
    @Nullable
    private BlockPos moveTargetPos;
    @Nullable
    private BlockPos lastFailedPos;

    private int nextStartTick;
    private int tryTicks;
    private int stuckTicks;
    private int actionTicks;
    private int ignoreFailedUntilTick;

    public ExtinguishTorchGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
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
        if (this.targetPos == null || this.tryTicks >= MAX_TRY_TICKS || this.stuckTicks >= MAX_STUCK_TICKS) {
            return false;
        }
        if (!canRunCommonChecks()) {
            return false;
        }
        return TorchExtinguishing.isTargetable(this.mob.level().getBlockState(this.targetPos));
    }

    @Override
    public void start() {
        this.tryTicks = 0;
        this.stuckTicks = 0;
        this.actionTicks = 0;
        setZombieAggressive(true);
        moveToTarget();
    }

    @Override
    public void stop() {
        if (this.targetPos != null && (this.stuckTicks >= MAX_STUCK_TICKS || this.tryTicks >= MAX_TRY_TICKS)) {
            this.lastFailedPos = this.targetPos;
            this.ignoreFailedUntilTick = this.mob.tickCount + FAILED_TARGET_COOLDOWN;
        }
        this.targetPos = null;
        this.moveTargetPos = null;
        this.actionTicks = 0;
        this.stuckTicks = 0;
        setZombieAggressive(false);
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

        Vec3 targetCenter = Vec3.atCenterOf(this.targetPos);
        this.mob.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z);

        if (isWithinManualReach(this.targetPos) && hasLineOfSightToBlock(this.targetPos)) {
            this.stuckTicks = 0;
            this.mob.getNavigation().stop();
            this.actionTicks++;

            if (this.actionTicks == MANUAL_SWING_TICK) {
                this.mob.swing(InteractionHand.MAIN_HAND);
            }

            if (this.actionTicks >= MANUAL_EXTINGUISH_DELAY) {
                if (this.mob.level() instanceof ServerLevel serverLevel) {
                    BlockState state = serverLevel.getBlockState(this.targetPos);
                    TorchExtinguishing.extinguish(serverLevel, this.targetPos, state, this.mob);
                }
                this.targetPos = null;
            }
        } else {
            this.actionTicks = 0;
            if (this.mob.getNavigation().isDone()) {
                this.stuckTicks++;
                if (this.stuckTicks % 10 == 1) {
                    moveToTarget();
                }
            } else {
                this.stuckTicks = 0;
                if (this.tryTicks % 20 == 0) {
                    moveToTarget();
                }
            }
        }
    }

    private void setZombieAggressive(boolean aggressive) {
        if (this.mob instanceof Zombie) {
            this.mob.setAggressive(aggressive);
        }
    }

    private boolean canRunCommonChecks() {
        if (!MobWhitelist.isAllowed(this.mob.getType())) {
            return false;
        }
        if (this.mob.getTarget() != null) {
            return false;
        }
        if (this.mob instanceof Witch witch && witch.isDrinkingPotion()) {
            return false;
        }
        return EventHooks.canEntityGrief(this.mob.level(), this.mob);
    }

    private void moveToTarget() {
        BlockPos dest = this.moveTargetPos != null ? this.moveTargetPos : this.targetPos;
        if (dest == null) {
            return;
        }
        Path path = this.mob.getNavigation().createPath(dest, 0);
        if (path != null) {
            this.mob.getNavigation().moveTo(path, this.speedModifier);
        } else {
            this.mob.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, this.speedModifier);
        }
    }

    private boolean isWithinManualReach(BlockPos pos) {
        double dx = (pos.getX() + 0.5) - this.mob.getX();
        double dz = (pos.getZ() + 0.5) - this.mob.getZ();
        double dy = pos.getY() - this.mob.getY();
        double horizontalReach = 1.05 + this.mob.getBbWidth() * 0.5;
        double maxVerticalReach = Math.max(2.2, this.mob.getBbHeight() + 0.4);
        return (dx * dx + dz * dz) <= horizontalReach * horizontalReach
                && dy >= -1.2
                && dy <= maxVerticalReach;
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

        boolean ignoreFailed = this.lastFailedPos != null && this.mob.tickCount < this.ignoreFailedUntilTick;

        for (int dy = -VERTICAL_SEARCH_RANGE; dy <= VERTICAL_SEARCH_RANGE; dy++) {
            int y = origin.getY() + dy;
            if (level.isOutsideBuildHeight(y)) {
                continue;
            }
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, y, origin.getZ() + dz);
                    if (ignoreFailed && cursor.equals(this.lastFailedPos)) {
                        continue;
                    }
                    double distSq = cursor.distSqr(origin);
                    if (distSq >= bestDistSq || !level.isLoaded(cursor)) {
                        continue;
                    }
                    if (TorchExtinguishing.isTargetable(level.getBlockState(cursor))) {
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
        for (int down = 0; down <= 2; down++) {
            BlockPos candidate = torchPos.below(down);
            if (isStandable(level, candidate)) {
                return candidate;
            }
        }
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
