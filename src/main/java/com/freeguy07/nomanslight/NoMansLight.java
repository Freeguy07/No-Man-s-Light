package com.freeguy07.nomanslight;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(NoMansLight.MODID)
public class NoMansLight {
    public static final String MODID = "nomanslight";

    public NoMansLight(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
