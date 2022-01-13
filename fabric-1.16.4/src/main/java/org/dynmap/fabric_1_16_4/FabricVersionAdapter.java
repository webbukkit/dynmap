package org.dynmap.fabric_1_16_4;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.MessageType;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import net.minecraft.util.collection.PackedIntegerArray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;
import org.dynmap.fabric_1_16_4.mixin.BiomeEffectsAccessor;
import org.dynmap.fabric.FabricAdapter;
import org.dynmap.fabric.FabricVersionInterface;

import java.io.IOException;
import java.util.Iterator;
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
        float brightnessTable[] = new float[16];
        for (int i=0; i<16; i++) {
            brightnessTable[i] = world.getDimension().method_28516(i);
        }
        return brightnessTable;
    }

    @Override
    public GenericNBTCompound ThreadedAnvilChunkStorage_getGenericNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException {
        return NBTCompound.newOrNull(tacs.getNbt(chunkPos));
    }

    @Override
    public GenericNBTCompound NbtCompound_getGenericNbt(Object nbtCompound) {
        return NBTCompound.newOrNull((CompoundTag) nbtCompound);
    }

    @Override
    public void ServerPlayerEntity_sendMessage(ServerPlayerEntity player, String message) {
        player.sendSystemMessage(new LiteralText(message), Util.NIL_UUID);
    }

    @Override
    public void MinecraftServer_broadcastMessage(MinecraftServer server, String message) {
        server.getPlayerManager().broadcastChatMessage(new LiteralText(message), MessageType.SYSTEM, Util.NIL_UUID);
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
        RegistryKey<World> registryKey = world.getRegistryKey();
        if (registryKey == World.OVERWORLD) {
            return world.getServer().getSaveProperties().getLevelName();
        } else if (registryKey == World.END) {
            return "DIM1";
        } else if (registryKey == World.NETHER) {
            return "DIM-1";
        } else {
            return registryKey.getValue().getNamespace() + "_" + registryKey.getValue().getPath();
        }
    }

    @Override
    public int BlockState_getRawId(BlockState blockState) {
        return Block.STATE_IDS.getRawId(blockState);
    }

    @Override
    public boolean World_isNether(World world) {
        return world.getRegistryKey() == World.NETHER;
    }

    @Override
    public boolean World_isEnd(World world) {
        return world.getRegistryKey() == World.END;
    }

    @Override
    public String World_getDefaultTitle(World world) {
        return world.getRegistryKey().getValue().getPath();
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
        return blockState.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
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
        return server.getRegistryManager().get(Registry.BIOME_KEY);
    }

    @Override
    public float Biome_getPrecipitation(Biome biome) {
        return biome.getDownfall();
    }

    @Override
    public int Biome_getWaterColor(Biome biome) {
        return ((BiomeEffectsAccessor) biome.getEffects()).getWaterColor();
    }

    @Override
    public CompletableFuture<Chunk> ChunkHolder_getSavingFuture(ChunkHolder chunk) {
        return chunk.getSavingFuture();
    }

    @Override
    public Iterator<BlockState> getBlockStateIdsIterator() {
        return Block.STATE_IDS.iterator();
    }

    @Override
    public boolean BlockState_isWaterlogged(BlockState blockState) {
        return ((!blockState.getFluidState().isEmpty()) && !(blockState.getBlock() instanceof FluidBlock));
    }

    @Override
    public String BlockState_getStateName(BlockState blockState) {
        String statename = "";
        for (net.minecraft.state.property.Property<?> p : blockState.getProperties()) {
            if (statename.length() > 0) {
                statename += ",";
            }
            statename += p.getName() + "=" + blockState.get(p).toString();
        }
        return statename;
    }

    @Override
    public BlockPos World_getSpawnPos(World world) {
        return new BlockPos(world.getLevelProperties().getSpawnX(),
                world.getLevelProperties().getSpawnY(),
                world.getLevelProperties().getSpawnZ());
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
