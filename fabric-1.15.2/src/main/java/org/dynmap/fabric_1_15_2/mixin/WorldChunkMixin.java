package org.dynmap.fabric_1_15_2.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.dynmap.fabric_1_15_2.event.BlockEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Shadow
    public abstract World getWorld();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    public void setBlockState(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> info) {
        if (info.getReturnValue() != null) {
            BlockEvents.EVENT.invoker().onBlockEvent(this.getWorld(), pos);
        }
    }
}
