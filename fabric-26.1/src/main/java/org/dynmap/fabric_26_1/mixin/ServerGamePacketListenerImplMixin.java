package org.dynmap.fabric_26_1.mixin;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;

import org.dynmap.fabric_26_1.access.SignUpdatePacketAccess;
import org.dynmap.fabric_26_1.event.BlockEvents;
import org.dynmap.fabric_26_1.event.ServerChatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "broadcastChatMessage",
            at = @At(
                    value = "HEAD"
            )
    )
    public void broadcastChatMessage(PlayerChatMessage signedMessage, CallbackInfo ci) {
        ServerChatEvents.EVENT.invoker().onChatMessage(player, signedMessage.decoratedContent().getString());
    }

    /**
     * Fires {@link BlockEvents#SIGN_CHANGE_EVENT} and lets listeners rewrite the sign lines.
     * <p>
     * Only {@code updateSignText(ServerboundSignUpdatePacket, List<FilteredText>)} is targeted: its signature is
     * identical from 26.1 to 26.3, whereas the packet accessors and {@code SignBlockEntity.updateSignText} changed
     * in 26.3 (boolean front text replaced by {@code SignTextSlot}). Replacing the list argument at HEAD keeps the
     * vanilla validation and the vanilla call to {@code SignBlockEntity.updateSignText} untouched.
     */
    @ModifyVariable(method = "updateSignText", at = @At("HEAD"), argsOnly = true)
    private List<FilteredText> dynmap$onUpdateSignText(List<FilteredText> signText, ServerboundSignUpdatePacket packet) {
        ServerLevel serverWorld = this.player.level();
        BlockPos blockPos = SignUpdatePacketAccess.pos(packet);
        // Mirror the vanilla checks so the event only fires for a real, loaded sign.
        if (blockPos == null || !serverWorld.hasChunkAt(blockPos)
                || !(serverWorld.getBlockEntity(blockPos) instanceof SignBlockEntity)) {
            return signText;
        }

        // Pull the raw text from the input.
        String[] rawTexts = new String[4];
        for (int i = 0; i < signText.size(); i++)
            rawTexts[i] = signText.get(i).raw();

        // Fire the event.
        BlockEvents.SIGN_CHANGE_EVENT.invoker().onSignChange(serverWorld, blockPos, rawTexts, player,
                SignUpdatePacketAccess.isFrontText(packet));

        // Hand the (possibly edited) lines back to vanilla.
        return Arrays.stream(rawTexts).map((raw) -> new FilteredText(raw, FilterMask.PASS_THROUGH)).toList();
    }
}
