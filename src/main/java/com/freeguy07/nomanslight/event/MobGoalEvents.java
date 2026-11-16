package com.freeguy07.nomanslight.event;

import com.freeguy07.nomanslight.NoMansLight;
import com.freeguy07.nomanslight.entity.ai.ExtinguishTorchGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = NoMansLight.MODID)
public class MobGoalEvents {
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof Witch witch) {
            addGoalIfAbsent(witch, 1);
        } else if (entity instanceof Zombie zombie) {
            addGoalIfAbsent(zombie, 5);
        } else if (entity instanceof AbstractSkeleton skeleton && !(skeleton instanceof WitherSkeleton)) {
            addGoalIfAbsent(skeleton, 4);
        }
    }

    private static void addGoalIfAbsent(PathfinderMob mob, int priority) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ExtinguishTorchGoal) {
                return;
            }
        }
        mob.goalSelector.addGoal(priority, new ExtinguishTorchGoal(mob, 1.0));
    }
}
