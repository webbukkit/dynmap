package org.dynmap.fabric_1_18.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ProtoChunk;
import org.dynmap.fabric_1_18.access.ProtoChunkAccessor;
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
                    target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockState;"
            )
    )
    public void setBlockState(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> info) {
        touchedByWorldGen = true;
    }

    public boolean getTouchedByWorldGen() {
        return touchedByWorldGen;
    }
}