package org.dynmap.fabric_1_14_4;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.text.LiteralText;
import net.minecraft.util.PackedIntegerArray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;
import org.dynmap.fabric_common.FabricAdapter;
import org.dynmap.fabric_common.FabricVersionInterface;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FabricVersionAdapter implements FabricVersionInterface, ModInitializer {

    @Override
    public void onInitialize() {
        FabricAdapter.VERSION_SPECIFIC = new FabricVersionAdapter();
    }

    @Override
    public float[] World_getBrightnessTable(World world) {
        return world.getDimension().getLightLevelToBrightness();
    }

    @Override
    public GenericNBTCompound ThreadedAnvilChunkStorage_getGenericNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException {
        return NBTCompound.newOrNull(tacs.getTagAt(chunkPos));
    }

    @Override
    public GenericNBTCompound NbtCompound_getGenericNbt(Object nbtCompound) {
        return NBTCompound.newOrNull((CompoundTag) nbtCompound);
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

    @Override
    public int World_getMinimumY(World world) {
        return 0;
    }

    @Override
    public int maxWorldHeight() {
        return 256;
    }

    @Override
    public boolean BlockState_isOpaqueFullCube(BlockState blockState) {
        return blockState.isFullOpaque(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    @Override
    public Optional<GameProfile> MinecraftServer_getProfileByName(MinecraftServer server, String username) {
        return Optional.of(server.getUserCache().findByName(username));
    }

    @Override
    public boolean MinecraftServer_isSinglePlayer(MinecraftServer server) {
        return server.isSinglePlayer();
    }

    @Override
    public String MinecraftServer_getSinglePlayerName(MinecraftServer server) {
        return server.getUserName();
    }

    @Override
    public Registry<Biome> MinecraftServer_getBiomeRegistry(MinecraftServer server) {
        return Registry.BIOME;
    }

    @Override
    public float Biome_getPrecipitation(Biome biome) {
        return biome.getRainfall();
    }

    @Override
    public int Biome_getWaterColor(Biome biome) {
        return biome.getWaterColor();
    }

    @Override
    public CompletableFuture<Chunk> ChunkHolder_getSavingFuture(ChunkHolder chunk) {
        return chunk.getFuture();
    }

    public static class NBTCompound implements GenericNBTCompound {
        private final CompoundTag obj;
        public static NBTCompound newOrNull(CompoundTag t) {
            return (t != null) ? new NBTCompound(t) : null;
        }
        public NBTCompound(CompoundTag t) {
            this.obj = t;
        }
        @Override
        public Set<String> getAllKeys() {
            return obj.getKeys();
        }
        @Override
        public boolean contains(String s) {
            return obj.contains(s);
        }
        @Override
        public boolean contains(String s, int i) {
            return obj.contains(s, i);
        }
        @Override
        public byte getByte(String s) {
            return obj.getByte(s);
        }
        @Override
        public short getShort(String s) {
            return obj.getShort(s);
        }
        @Override
        public int getInt(String s) {
            return obj.getInt(s);
        }
        @Override
        public long getLong(String s) {
            return obj.getLong(s);
        }
        @Override
        public float getFloat(String s) {
            return obj.getFloat(s);
        }
        @Override
        public double getDouble(String s) {
            return obj.getDouble(s);
        }
        @Override
        public String getString(String s) {
            return obj.getString(s);
        }
        @Override
        public byte[] getByteArray(String s) {
            return obj.getByteArray(s);
        }
        @Override
        public int[] getIntArray(String s) {
            return obj.getIntArray(s);
        }
        @Override
        public long[] getLongArray(String s) {
            return obj.getLongArray(s);
        }
        @Override
        public GenericNBTCompound getCompound(String s) {
            return new NBTCompound(obj.getCompound(s));
        }
        @Override
        public GenericNBTList getList(String s, int i) {
            return new NBTList(obj.getList(s, i));
        }
        @Override
        public boolean getBoolean(String s) {
            return obj.getBoolean(s);
        }
        @Override
        public String getAsString(String s) {
            return obj.get(s).asString();
        }
        @Override
        public GenericBitStorage makeBitStorage(int bits, int count, long[] data) {
            return new OurBitStorage(bits, count, data);
        }
        public String toString() {
            return obj.toString();
        }
    }

    public static class NBTList implements GenericNBTList {
        private final ListTag obj;
        public NBTList(ListTag t) {
            obj = t;
        }
        @Override
        public int size() {
            return obj.size();
        }
        @Override
        public String getString(int idx) {
            return obj.getString(idx);
        }
        @Override
        public GenericNBTCompound getCompound(int idx) {
            return new NBTCompound(obj.getCompound(idx));
        }
        public String toString() {
            return obj.toString();
        }
    }

    public static class OurBitStorage implements GenericBitStorage {
        private final PackedIntegerArray bs;
        public OurBitStorage(int bits, int count, long[] data) {
            bs = new PackedIntegerArray(bits, count, data);
        }
        @Override
        public int get(int idx) {
            return bs.get(idx);
        }
    }
}
