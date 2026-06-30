package org.dynmap.fabric_26_1.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import org.dynmap.fabric_26_1.event.BlockEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class WorldChunkMixin {
    @Shadow
    public abstract Level getWorld();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    public void setBlockState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> info) {
        if (info.getReturnValue() != null) {
            BlockEvents.BLOCK_EVENT.invoker().onBlockEvent(this.getWorld(), pos);
        }
    }
}
