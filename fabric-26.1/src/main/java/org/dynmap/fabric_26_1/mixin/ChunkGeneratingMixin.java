package org.dynmap.fabric_26_1.mixin;

import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.util.StaticCache2D;

import org.dynmap.fabric_26_1.access.ProtoChunkAccessor;
import org.dynmap.fabric_26_1.event.CustomServerChunkEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkStatusTasks.class, priority = 666 /* fire before Fabric API CHUNK_LOAD event */)
public abstract class ChunkGeneratingMixin {
    
    @Inject(method = "passThrough", at = @At("TAIL"))
    private static void dynmap$onChunkGenerate(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<?> cir
    ) {
        if (((ProtoChunkAccessor)chunk).getTouchedByWorldGen()) {
            CustomServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(context.level(), chunk);
        }
    }
    // @Inject(
    //         /* Same place as fabric-lifecycle-events-v1 event CHUNK_LOAD (we will fire before it) */
    //         method = "passThrough",
    //         at = @At("TAIL")
    // )
    // private static void onChunkGenerate(ChunkAccess chunk, WorldGenContext chunkGenerationContext, GenerationChunkHolder chunkHolder, CallbackInfoReturnable<ChunkAccess> callbackInfoReturnable) {
    //     if (((ProtoChunkAccessor)chunk).getTouchedByWorldGen()) {
    //         CustomServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(chunkGenerationContext.level(), callbackInfoReturnable.getReturnValue());
    //     }
    // }
}
