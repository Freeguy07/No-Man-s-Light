package com.freeguy07.nomanslight;

import com.freeguy07.nomanslight.event.MobGoalEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(NoMansLight.MODID)
public class NoMansLight {
    public static final String MODID = "nomanslight";

    public NoMansLight(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(MobGoalEvents::onConfigLoad);
        modEventBus.addListener(MobGoalEvents::onConfigReload);
    }
}
