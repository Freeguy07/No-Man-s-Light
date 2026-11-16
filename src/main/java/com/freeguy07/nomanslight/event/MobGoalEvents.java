package com.freeguy07.nomanslight.event;

import com.freeguy07.nomanslight.Config;
import com.freeguy07.nomanslight.NoMansLight;
import com.freeguy07.nomanslight.entity.MobWhitelist;
import com.freeguy07.nomanslight.entity.ai.ExtinguishTorchGoal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = NoMansLight.MODID)
public class MobGoalEvents {
    private static final int DEFAULT_GOAL_PRIORITY = 4;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof PathfinderMob mob && MobWhitelist.isAllowed(mob.getType())) {
            addGoalIfAbsent(mob);
        }
    }

    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            MobWhitelist.invalidate();
        }
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != Config.SPEC) {
            return;
        }
        MobWhitelist.invalidate();

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> syncLoadedMobs(server));
        }
    }

    private static void syncLoadedMobs(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PathfinderMob mob && MobWhitelist.isAllowed(mob.getType())) {
                    addGoalIfAbsent(mob);
                }
            }
        }
    }

    private static void addGoalIfAbsent(PathfinderMob mob) {
        int minStrollPriority = Integer.MAX_VALUE;
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ExtinguishTorchGoal) {
                return;
            }
            if (wrapped.getGoal() instanceof RandomStrollGoal) {
                minStrollPriority = Math.min(minStrollPriority, wrapped.getPriority());
            }
        }
        int priority = minStrollPriority != Integer.MAX_VALUE
                ? Math.max(1, minStrollPriority - 1)
                : DEFAULT_GOAL_PRIORITY;
        mob.goalSelector.addGoal(priority, new ExtinguishTorchGoal(mob, 1.0));
    }
}
