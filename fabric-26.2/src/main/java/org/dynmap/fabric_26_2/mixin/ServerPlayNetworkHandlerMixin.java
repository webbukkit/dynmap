package org.dynmap.fabric_26_2.mixin;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.dynmap.fabric_26_2.event.BlockEvents;
import org.dynmap.fabric_26_2.event.ServerChatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayer player;

    // Injected at HEAD (before Mojang's chat filtering runs) so Dynmap sees the raw, unfiltered message.
    @Inject(method = "handleChat", at = @At("HEAD"))
    public void onGameMessage(ServerboundChatPacket packet, CallbackInfo ci) {
        ServerChatEvents.EVENT.invoker().onChatMessage(player, packet.message());
    }

    // Injected at HEAD (before Mojang's profanity filtering runs) so Dynmap sees the raw sign text
    // the player actually typed, same intent as the old local-capture/cancel-and-replay approach,
    // but reading straight from the packet instead (which now exposes getLines()/getPos() directly).
    @Inject(method = "handleSignUpdate", at = @At("HEAD"))
    public void onSignUpdate(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        BlockEvents.SIGN_CHANGE_EVENT.invoker().onSignChange((ServerLevel) player.level(), packet.getPos(), packet.getLines(), player, packet.isFrontText());
    }
}
