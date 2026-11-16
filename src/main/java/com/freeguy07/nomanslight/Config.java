package com.freeguy07.nomanslight;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TARGET_SCONCES = BUILDER
            .comment("Whether eligible mobs can target and extinguish No Man's Land sconces in addition to torches.")
            .define("targetSconces", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
