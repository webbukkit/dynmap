package org.dynmap.fabric_1_21_7.mixin;

import net.minecraft.world.chunk.ChunkGenerating;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.Chunk;

import org.dynmap.fabric_1_21_7.access.ProtoChunkAccessor;
import org.dynmap.fabric_1_21_7.event.CustomServerChunkEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkGenerating.class, priority = 666 /* fire before Fabric API CHUNK_LOAD event */)
public abstract class ChunkGeneratingMixin {
    @Inject(
            /* Same place as fabric-lifecycle-events-v1 event CHUNK_LOAD (we will fire before it) */
            method = "method_60553",
            at = @At("TAIL")
    )
    private static void onChunkGenerate(Chunk chunk, ChunkGenerationContext chunkGenerationContext, AbstractChunkHolder chunkHolder, CallbackInfoReturnable<Chunk> callbackInfoReturnable) {
        if (((ProtoChunkAccessor)chunk).getTouchedByWorldGen()) {
            CustomServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(chunkGenerationContext.world(), callbackInfoReturnable.getReturnValue());
        }
    }
}
