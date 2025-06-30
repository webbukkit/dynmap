package org.dynmap.fabric_1_21_7.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;

import org.dynmap.fabric_1_21_7.event.PlayerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
//    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("RETURN"))
//    public void teleport(ServerWorld targetWorld, double x, double y, double z, float yaw, float pitch, CallbackInfo info) {
//        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
//        if (targetWorld != player.getServerWorld()) {
//            PlayerEvents.PLAYER_CHANGED_DIMENSION.invoker().onPlayerChangedDimension(player);
//        }
//    }

    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/entity/Entity;", at = @At("TAIL"))
    public void teleportTo(TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> info) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getRemovalReason() == null) {
            PlayerEvents.PLAYER_CHANGED_DIMENSION.invoker().onPlayerChangedDimension(player);
        }
    }
}
