package org.dynmap.fabric_1_20_4.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.network.message.FilterMask;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.filter.FilteredMessage;
import net.minecraft.server.filter.TextStream;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;

import org.dynmap.fabric_1_20_4.event.BlockEvents;
import org.dynmap.fabric_1_20_4.event.ServerChatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
            method = "handleDecoratedMessage",
            at = @At(
                    value = "HEAD"
            )
    )
    public void onGameMessage(SignedMessage signedMessage, CallbackInfo ci) {
        ServerChatEvents.EVENT.invoker().onChatMessage(player, signedMessage.getContent().getString());
    }

    @Inject(
            method = "onSignUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/entity/SignBlockEntity;tryChangeText(Lnet/minecraft/entity/player/PlayerEntity;ZLjava/util/List;)V",
                    shift = At.Shift.BEFORE
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true
    )
    public void onSignUpdate(UpdateSignC2SPacket packet, List<FilteredMessage> signText, CallbackInfo ci,
                             ServerWorld serverWorld, BlockPos blockPos, BlockEntity blockEntity, SignBlockEntity signBlockEntity)
    {
        // Pull the raw text from the input.
        String[] rawTexts = new String[4];
        for (int i=0; i<signText.size(); i++)
            rawTexts[i] = signText.get(i).raw();

        // Fire the event.
        BlockEvents.SIGN_CHANGE_EVENT.invoker().onSignChange(serverWorld, blockPos, rawTexts, player, packet.isFront());

        // Rebuild the signText list with the new values.
        List<FilteredMessage> newSignText = Arrays.stream(rawTexts).map((raw) -> new FilteredMessage(raw, FilterMask.PASS_THROUGH)).toList();

        // Execute the setting of the texts with the edited values.
        signBlockEntity.tryChangeText(this.player, packet.isFront(), newSignText);

        // Cancel the original tryChangeText() since we're calling it ourselves above.
        ci.cancel();
    }
}