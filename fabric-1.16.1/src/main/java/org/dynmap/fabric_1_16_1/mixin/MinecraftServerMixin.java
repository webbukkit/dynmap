package org.dynmap.fabric_1_16_1.mixin;

import net.minecraft.server.MinecraftServer;
import org.dynmap.fabric_1_16_1.event.CustomServerLifecycleEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "loadWorld", at = @At("HEAD"))
    protected void loadWorld(CallbackInfo info) {
        CustomServerLifecycleEvents.SERVER_STARTED_PRE_WORLD_LOAD.invoker().onServerStarted((MinecraftServer) (Object) this);
    }
}
