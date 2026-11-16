package com.freeguy07.nomanslight.entity;

import com.freeguy07.nomanslight.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class MobWhitelist {
    private static volatile Snapshot cached;

    private MobWhitelist() {
    }

    public static void invalidate() {
        cached = null;
    }

    public static boolean isAllowed(EntityType<?> type) {
        Snapshot snapshot = cached;
        if (snapshot == null) {
            snapshot = build();
            cached = snapshot;
        }
        if (snapshot.directTypes.contains(type)) {
            return true;
        }
        for (TagKey<EntityType<?>> tag : snapshot.tags) {
            if (type.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static Snapshot build() {
        Set<EntityType<?>> directTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();

        for (String entry : Config.MOB_WHITELIST.get()) {
            if (entry.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
                if (tagId != null) {
                    tags.add(TagKey.create(Registries.ENTITY_TYPE, tagId));
                }
            } else {
                ResourceLocation entityId = ResourceLocation.tryParse(entry);
                if (entityId != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).ifPresent(directTypes::add);
                }
            }
        }

        return new Snapshot(directTypes, List.copyOf(tags));
    }

    private record Snapshot(Set<EntityType<?>> directTypes, List<TagKey<EntityType<?>>> tags) {
    }
}
