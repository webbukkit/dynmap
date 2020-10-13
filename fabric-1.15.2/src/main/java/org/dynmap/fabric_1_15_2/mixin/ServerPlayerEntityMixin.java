package org.dynmap.fabric_1_15_2.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.dimension.DimensionType;
import org.dynmap.fabric_1_15_2.event.PlayerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(method = "teleport", at = @At("RETURN"))
    public void teleport(ServerWorld targetWorld, double x, double y, double z, float yaw, float pitch, CallbackInfo info) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (targetWorld != player.world) {
            PlayerEvents.PLAYER_CHANGED_DIMENSION.invoker().onPlayerChangedDimension(player);
        }
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    public void changeDimension(DimensionType destination, CallbackInfoReturnable<Entity> info) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (!player.removed) {
            PlayerEvents.PLAYER_CHANGED_DIMENSION.invoker().onPlayerChangedDimension(player);
        }
    }
}
