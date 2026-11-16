package com.freeguy07.nomanslight;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final List<String> DEFAULT_MOB_WHITELIST = List.of(
            "minecraft:witch",
            "minecraft:zombie",
            "minecraft:zombie_villager",
            "minecraft:husk",
            "minecraft:drowned",
            "minecraft:zombified_piglin",
            "minecraft:skeleton",
            "minecraft:stray",
            "minecraft:bogged"
    );

    public static final ModConfigSpec.BooleanValue TARGET_SCONCES = BUILDER
            .comment("Whether eligible mobs can target and extinguish No Man's Land sconces in addition to torches.")
            .define("targetSconces", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOB_WHITELIST = BUILDER
            .comment("Entity IDs (e.g. \"minecraft:zombie\") or entity type tags (e.g. \"#minecraft:zombies\") of mobs that will target and extinguish torches.")
            .defineListAllowEmpty("mobWhitelist", DEFAULT_MOB_WHITELIST, () -> "minecraft:zombie", Config::validateMobEntry);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateMobEntry(Object obj) {
        if (!(obj instanceof String entry) || entry.isEmpty()) {
            return false;
        }
        String raw = entry.startsWith("#") ? entry.substring(1) : entry;
        if (raw.isEmpty()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id != null && !id.getPath().isEmpty();
    }
}
