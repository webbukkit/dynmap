package org.dynmap.fabric_1_18.mixin;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.Chunk;
import org.dynmap.fabric_1_18.access.ProtoChunkAccessor;
import org.dynmap.fabric_1_18.event.CustomServerChunkEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ThreadedAnvilChunkStorage.class, priority = 666 /* fire before Fabric API CHUNK_LOAD event */)
public abstract class ThreadedAnvilChunkStorageMixin {
    @Final
    @Shadow
    ServerWorld world;

    @Inject(
            /* Same place as fabric-lifecycle-events-v1 event CHUNK_LOAD (we will fire before it) */
            method = "method_17227",
            at = @At("TAIL")
    )
    private void onChunkGenerate(ChunkHolder chunkHolder, Chunk protoChunk, CallbackInfoReturnable<Chunk> callbackInfoReturnable) {
        if (((ProtoChunkAccessor)protoChunk).getTouchedByWorldGen()) {
            CustomServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(this.world, callbackInfoReturnable.getReturnValue());
        }
    }
}