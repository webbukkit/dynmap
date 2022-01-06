package org.dynmap.fabric_1_15_2;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.fabric_helper.FabricVersionInterface;

import java.io.IOException;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        float brightnessTable[] = new float[16];
        for (int i=0; i<16; i++) {
            brightnessTable[i] = world.getDimension().getBrightness(i);
        }
        return brightnessTable;
    }

    @Override
    public GenericNBTCompound ThreadedAnvilChunkStorage_getNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException {
        return new NBT.NBTCompound(tacs.getNbt(chunkPos));
    }

    @Override
    public void ServerPlayerEntity_sendMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(new LiteralText(message));
    }

    @Override
    public void MinecraftServer_broadcastMessage(MinecraftServer server, String message) {
        server.getPlayerManager().broadcastChatMessage(new LiteralText(message), true);
    }

    @Override
    public void ServerPlayerEntity_sendTitleText(ServerPlayerEntity player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.networkHandler.sendPacket(new TitleS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
        if (title != null) {
            player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText(title)));
        }
        if (subtitle != null) {
            player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.SUBTITLE, new LiteralText(subtitle)));
        }
    }

    @Override
    public String World_getDimensionName(World world) {
        DimensionType dimensionType = world.getDimension().getType();
        if (dimensionType == DimensionType.OVERWORLD) {
            return world.getLevelProperties().getLevelName();
        } else if (dimensionType == DimensionType.THE_END) {
            return "DIM1";
        } else if (dimensionType == DimensionType.THE_NETHER) {
            return "DIM-1";
        } else {
            return dimensionType.toString();
        }
    }

    @Override
    public int BlockState_getRawId(BlockState blockState) {
        return Block.STATE_IDS.getId(blockState);
    }

    @Override
    public boolean World_isNether(World world) {
        return world.getDimension().getType() == DimensionType.THE_NETHER;
    }

    @Override
    public boolean World_isEnd(World world) {
        return world.getDimension().getType() == DimensionType.THE_END;
    }

    @Override
    public String World_getDefaultTitle(World world) {
        /* FIXME: This doesn't match the newer version, use toString() instead */
        return String.format("world%s", world.getDimension().getType().getSuffix());
    }

}
