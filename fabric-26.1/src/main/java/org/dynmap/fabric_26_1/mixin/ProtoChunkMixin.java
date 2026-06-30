package org.dynmap.fabric_26_1.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ProtoChunk;

import org.dynmap.fabric_26_1.access.ProtoChunkAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProtoChunk.class)
public class ProtoChunkMixin implements ProtoChunkAccessor {
    private boolean touchedByWorldGen = false;

    @Inject(
            method = "setBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    public void setBlockState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> info) {
        touchedByWorldGen = true;
    }

    public boolean getTouchedByWorldGen() {
        return touchedByWorldGen;
    }
}
