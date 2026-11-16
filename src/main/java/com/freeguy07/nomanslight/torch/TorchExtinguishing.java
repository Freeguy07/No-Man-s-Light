package com.freeguy07.nomanslight.torch;

import com.farcr.nomansland.common.block.torches.ExtinguishableBlockPairing;
import com.farcr.nomansland.common.block.torches.SconceTorchBlock;
import com.farcr.nomansland.common.block.torches.SconceWallTorchBlock;
import com.farcr.nomansland.common.registry.NMLRegistries;
import com.farcr.nomansland.common.registry.NMLSounds;
import com.freeguy07.nomanslight.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TorchExtinguishing {
    private static volatile Map<Block, ExtinguishableBlockPairing> litBlockIndex;

    private TorchExtinguishing() {
    }

    private static Map<Block, ExtinguishableBlockPairing> getLitBlockIndex() {
        Map<Block, ExtinguishableBlockPairing> index = litBlockIndex;
        if (index == null) {
            Map<Block, ExtinguishableBlockPairing> built = new IdentityHashMap<>();
            for (ExtinguishableBlockPairing pairing : NMLRegistries.EXTINGUISHABLE_BLOCKS) {
                built.put(pairing.litBlock(), pairing);
            }
            litBlockIndex = built;
            index = built;
        }
        return index;
    }

    public static boolean isSconce(Block block) {
        return block instanceof SconceTorchBlock || block instanceof SconceWallTorchBlock;
    }

    @Nullable
    public static ExtinguishableBlockPairing getTargetPairing(BlockState state) {
        Block block = state.getBlock();
        ExtinguishableBlockPairing pairing = getLitBlockIndex().get(block);
        if (pairing == null) {
            return null;
        }
        if (isSconce(block) && !Config.TARGET_SCONCES.getAsBoolean()) {
            return null;
        }
        return pairing;
    }

    public static boolean isTargetable(BlockState state) {
        return getTargetPairing(state) != null;
    }

    public static boolean extinguish(ServerLevel level, BlockPos pos, BlockState state, @Nullable LivingEntity actor) {
        if (actor != null && !EventHooks.canEntityGrief(level, actor)) {
            return false;
        }
        ExtinguishableBlockPairing pairing = getTargetPairing(state);
        if (pairing == null) {
            return false;
        }

        level.playSound(null, pos, NMLSounds.TORCH_EXTINGUISH.get(), SoundSource.BLOCKS, 0.4F, 1.0F);
        level.gameEvent(actor, GameEvent.BLOCK_CHANGE, pos);
        level.setBlockAndUpdate(pos, pairing.extinguishedBlock().withPropertiesOf(state));
        level.sendParticles(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5,
                pos.getY() + 0.6,
                pos.getZ() + 0.5,
                4,
                0.1,
                0.1,
                0.1,
                0.01
        );
        return true;
    }
}
