package org.dynmap.fabric_1_19_1.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.filter.FilteredMessage;
import net.minecraft.server.filter.TextStream;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import net.minecraft.util.registry.RegistryKey;
import org.dynmap.fabric_1_19_1.event.BlockEvents;
import org.dynmap.fabric_1_19_1.event.ServerChatEvents;
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
                    target = "Lnet/minecraft/block/entity/SignBlockEntity;markDirty()V",
                    shift = At.Shift.BEFORE
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void onSignUpdate(UpdateSignC2SPacket packet, List<FilteredMessage> signText, CallbackInfo info,
            ServerWorld serverWorld, BlockPos blockPos, BlockState blockState, BlockEntity blockEntity, SignBlockEntity signBlockEntity)
    {
        // Pull the raw text from the input.
        String[] rawTexts = new String[4];
        for (int i=0; i<signText.size(); i++)
            rawTexts[i] = signText.get(i).raw();

        // Fire the event.
        BlockEvents.SIGN_CHANGE_EVENT.invoker().onSignChange(serverWorld, blockPos, rawTexts, blockState.getMaterial(), player);

        // Put the (possibly updated) texts in the sign. Ignore filtering (is this OK?).
        for (int i=0; i<signText.size(); i++)
            signBlockEntity.setTextOnRow(i, Text.literal(rawTexts[i]));
    }
}