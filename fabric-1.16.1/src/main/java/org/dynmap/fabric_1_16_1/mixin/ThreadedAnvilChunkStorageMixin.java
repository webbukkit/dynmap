package org.dynmap.fabric_1_16_1.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.Chunk;
import org.dynmap.fabric_1_16_1.event.ChunkDataEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageMixin {
    @Shadow
    @Final
    private ServerWorld world;

    @Inject(method = "save(Lnet/minecraft/world/chunk/Chunk;)Z", at = @At("RETURN"))
    private void save(Chunk chunk, CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValueZ()) {
            ChunkDataEvents.SAVE.invoker().onSave(this.world, chunk);
        }
    }
}
