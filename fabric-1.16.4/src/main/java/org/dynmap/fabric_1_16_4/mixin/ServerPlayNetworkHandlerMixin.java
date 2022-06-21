package org.dynmap.fabric_1_16_4.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.text.LiteralText;
import org.dynmap.fabric_1_16_4.event.ServerChatEvents;
import org.dynmap.fabric_1_16_4.event.BlockEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
            method = "method_31286",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/PlayerManager;broadcastChatMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V",
                    shift = At.Shift.BEFORE
            )
    )
    public void onGameMessage(String string, CallbackInfo info) {
        ServerChatEvents.EVENT.invoker().onChatMessage(player, string);
    }

        @Inject(
            method = "method_31282",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/entity/SignBlockEntity;markDirty()V",
                    shift = At.Shift.BEFORE
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void onSignUpdate(UpdateSignC2SPacket packet, List<String> signText, CallbackInfo info,
            ServerWorld serverWorld, BlockPos blockPos, BlockState blockState, BlockEntity blockEntity, SignBlockEntity signBlockEntity)
    {
        // Pull the raw text from the input.
        String[] rawTexts = new String[4];
        for (int i=0; i<signText.size(); i++)
            rawTexts[i] = signText.get(i);

        // Fire the event.
        BlockEvents.SIGN_CHANGE_EVENT.invoker().onSignChange(serverWorld, blockPos, rawTexts, blockState.getMaterial(), player);

        // Put the (possibly updated) texts in the sign. Ignore filtering (is this OK?).
        for (int i=0; i<signText.size(); i++)
            signBlockEntity.setTextOnRow(i, new LiteralText(rawTexts[i]));
    }
}
