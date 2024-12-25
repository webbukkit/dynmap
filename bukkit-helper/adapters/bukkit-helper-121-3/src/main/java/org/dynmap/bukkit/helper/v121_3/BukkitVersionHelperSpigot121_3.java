package org.dynmap.bukkit.helper.v121_3;

import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluid;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.bukkit.helper.BukkitMaterial;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.bukkit.helper.BukkitVersionHelperGeneric.TexturesPayload;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperSpigot121_3 extends BukkitVersionHelper {

    @Override
    public boolean isUnsafeAsync() {
        return true;
    }

    /**
     * Get block short name list
     */
    @Override
    public String[] getBlockNames() {
        IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;
        Block baseb = null;
        Iterator<BlockState> iter = bsids.iterator();
        ArrayList<String> names = new ArrayList<String>();
        while (iter.hasNext()) {
            BlockState bs = iter.next();
            Block b = bs.getBlock();
            // If this is new block vs last, it's the base block state
            if (b != baseb) {
                baseb = b;
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            String bn = id.toString();
            if (bn != null) {
                names.add(bn);
                Log.info("block=" + bn);
            }
        }
        return names.toArray(new String[0]);
    }

    private static Registry<Biome> reg = null;

    private static Registry<Biome> getBiomeReg() {
        if (reg == null) {
            reg = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME);
        }
        return reg;
    }

    private Object[] biomelist;

    /**
     * Get list of defined biomebase objects
     */
    @Override
    public Object[] getBiomeBaseList() {
        if (biomelist == null) {
            biomelist = new Biome[256];
            Iterator<Biome> iter = getBiomeReg().iterator();
            while (iter.hasNext()) {
                Biome b = iter.next();
                int bidx = getBiomeReg().getId(b);
                if (bidx >= biomelist.length) {
                    biomelist = Arrays.copyOf(biomelist, bidx + biomelist.length);
                }
                biomelist[bidx] = b;
            }
        }
        return biomelist;
    }

    /**
     * Get ID from biomebase
     */
    @Override
    public int getBiomeBaseID(Object bb) {
        return getBiomeReg().getId((Biome) bb);
    }

    public static IdentityHashMap<BlockState, DynmapBlockState> dataToState;

    /**
     * Initialize block states (org.dynmap.blockstate.DynmapBlockState)
     */
    @Override
    public void initializeBlockStates() {
        dataToState = new IdentityHashMap<BlockState, DynmapBlockState>();
        HashMap<String, DynmapBlockState> lastBlockState = new HashMap<String, DynmapBlockState>();
        IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;
        Block baseb = null;
        Iterator<BlockState> iter = bsids.iterator();
        ArrayList<String> names = new ArrayList<String>();

        // Loop through block data states
        DynmapBlockState.Builder bld = new DynmapBlockState.Builder();
        while (iter.hasNext()) {
            BlockState bd = iter.next();
            Block b = bd.getBlock();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            String bname = id.toString();
            DynmapBlockState lastbs = lastBlockState.get(bname);    // See if we have seen this one
            int idx = 0;
            if (lastbs != null) {    // Yes
                idx = lastbs.getStateCount();    // Get number of states so far, since this is next
            }
            // Build state name
            String sb = "";
            String fname = bd.toString();
            int off1 = fname.indexOf('[');
            if (off1 >= 0) {
                int off2 = fname.indexOf(']');
                sb = fname.substring(off1 + 1, off2);
            }
            int lightAtten = bd.getLightBlock();
            // Fill in base attributes
            bld.setBaseState(lastbs).setStateIndex(idx).setBlockName(bname).setStateName(sb).setAttenuatesLight(lightAtten);
            if (bd.getSoundType() != null) {
                bld.setMaterial(bd.getSoundType().toString());
            }
            if (bd.isSolid()) {
                bld.setSolid();
            }
            if (bd.isAir()) {
                bld.setAir();
            }
            if (bd.is(BlockTags.OVERWORLD_NATURAL_LOGS)) {
                bld.setLog();
            }
            if (bd.is(BlockTags.LEAVES)) {
                bld.setLeaves();
            }
            if ((!bd.getFluidState().isEmpty())) {    // Test if fluid type for block is not empty
                bld.setWaterlogged();
            }
            DynmapBlockState dbs = bld.build(); // Build state

            dataToState.put(bd, dbs);
            lastBlockState.put(bname, (lastbs == null) ? dbs : lastbs);
            Log.verboseinfo("blk=" + bname + ", idx=" + idx + ", state=" + sb + ", waterlogged=" + dbs.isWaterlogged());
        }
    }

    /**
     * Create chunk cache for given chunks of given world
     *
     * @param dw     - world
     * @param chunks - chunk list
     * @return cache
     */
    @Override
    public MapChunkCache getChunkCache(BukkitWorld dw, List<DynmapChunk> chunks) {
        MapChunkCache121_3 c = new MapChunkCache121_3(gencache);
        c.setChunks(dw, chunks);
        return c;
    }

    /**
     * Get biome base water multiplier
     */
    @Override
    public int getBiomeBaseWaterMult(Object bb) {
        Biome biome = (Biome) bb;
        return biome.getWaterColor();
    }

    /**
     * Get temperature from biomebase
     */
    @Override
    public float getBiomeBaseTemperature(Object bb) {
        return ((Biome) bb).getBaseTemperature();
    }

    /**
     * Get humidity from biomebase
     */
    @Override
    public float getBiomeBaseHumidity(Object bb) {
        return ((Biome) bb).climateSettings.downfall();
    }

    @Override
    public Polygon getWorldBorder(World world) {
        Polygon p = null;
        WorldBorder wb = world.getWorldBorder();
        if (wb != null) {
            Location c = wb.getCenter();
            double size = wb.getSize();
            if ((size > 1) && (size < 1E7)) {
                size = size / 2;
                p = new Polygon();
                p.addVertex(c.getX() - size, c.getZ() - size);
                p.addVertex(c.getX() + size, c.getZ() - size);
                p.addVertex(c.getX() + size, c.getZ() + size);
                p.addVertex(c.getX() - size, c.getZ() + size);
            }
        }
        return p;
    }

    // Send title/subtitle to user
    public void sendTitleText(Player p, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
        if (p != null) {
            p.sendTitle(title, subtitle, fadeInTicks, stayTicks, fadeOutTIcks);
        }
    }

    /**
     * Get material map by block ID
     */
    @Override
    public BukkitMaterial[] getMaterialList() {
        return new BukkitMaterial[4096];    // Not used
    }

    @Override
    public void unloadChunkNoSave(World w, org.bukkit.Chunk c, int cx, int cz) {
        Log.severe("unloadChunkNoSave not implemented");
    }

    private String[] biomenames;

    @Override
    public String[] getBiomeNames() {
        if (biomenames == null) {
            biomenames = new String[256];
            Iterator<Biome> iter = getBiomeReg().iterator();
            while (iter.hasNext()) {
                Biome b = iter.next();
                int bidx = getBiomeReg().getId(b);
                if (bidx >= biomenames.length) {
                    biomenames = Arrays.copyOf(biomenames, bidx + biomenames.length);
                }
                biomenames[bidx] = b.toString();
            }
        }
        return biomenames;
    }

    @Override
    public String getStateStringByCombinedId(int blkid, int meta) {
        Log.severe("getStateStringByCombinedId not implemented");
        return null;
    }

    @Override
    /** Get ID string from biomebase */
    public String getBiomeBaseIDString(Object bb) {
        return getBiomeReg().getKey((Biome) bb).getPath();
    }

    @Override
    public String getBiomeBaseResourceLocsation(Object bb) {
        return getBiomeReg().getKey((Biome) bb).toString();
    }

    @Override
    public Object getUnloadQueue(World world) {
        Log.warning("getUnloadQueue not implemented yet");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
        Log.warning("isInUnloadQueue not implemented yet");
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css) {
        Log.warning("getBiomeBaseFromSnapshot not implemented yet");
        // TODO Auto-generated method stub
        return new Object[256];
    }

    @Override
    public long getInhabitedTicks(Chunk c) {
        return ((CraftChunk) c).getHandle(ChunkStatus.FULL).getInhabitedTime();
    }

    @Override
    public Map<?, ?> getTileEntitiesForChunk(Chunk c) {
        return ((CraftChunk) c).getHandle(ChunkStatus.FULL).blockEntities;
    }

    @Override
    public int getTileEntityX(Object te) {
        BlockEntity tileent = (BlockEntity) te;
        return tileent.getBlockPos().getX();
    }

    @Override
    public int getTileEntityY(Object te) {
        BlockEntity tileent = (BlockEntity) te;
        return tileent.getBlockPos().getY();
    }

    @Override
    public int getTileEntityZ(Object te) {
        BlockEntity tileent = (BlockEntity) te;
        return tileent.getBlockPos().getZ();
    }

    @Override
    public Object readTileEntityNBT(Object te, World world) {
        BlockEntity tileent = (BlockEntity) te;
        if (tileent.getLevel() == null) {
            return null;
        }
        return tileent.saveWithFullMetadata(((CraftWorld) world).getHandle().registryAccess());
    }

    @Override
    public Object getFieldValue(Object nbt, String field) {
        CompoundTag rec = (CompoundTag) nbt;
        Tag val = rec.get(field);
        if (val == null) return null;
        if (val instanceof ByteTag tag) {
            return tag.getAsByte();
        } else if (val instanceof ShortTag tag) {
            return tag.getAsShort();
        } else if (val instanceof IntTag tag) {
            return tag.getAsInt();
        } else if (val instanceof LongTag tag) {
            return tag.getAsLong();
        } else if (val instanceof FloatTag tag) {
            return tag.getAsFloat();
        } else if (val instanceof DoubleTag tag) {
            return tag.getAsDouble();
        } else if (val instanceof ByteArrayTag tag) {
            return tag.getAsByteArray();
        } else if (val instanceof StringTag tag) {
            return tag.getAsString();
        } else if (val instanceof IntArrayTag tag) {
            return tag.getAsIntArray();
        }
        return null;
    }

    @Override
    public Player[] getOnlinePlayers() {
        Collection<? extends Player> p = Bukkit.getServer().getOnlinePlayers();
        return p.toArray(new Player[0]);
    }

    @Override
    public double getHealth(Player p) {
        return p.getHealth();
    }

    private static final Gson gson = new GsonBuilder().create();

    /**
     * Get skin URL for player
     *
     * @param player
     */
    @Override
    public String getSkinURL(Player player) {
        String url = null;
        CraftPlayer cp = (CraftPlayer) player;
        GameProfile profile = cp.getProfile();
        if (profile != null) {
            PropertyMap pm = profile.getProperties();
            if (pm != null) {
                Collection<Property> txt = pm.get("textures");
                Property textureProperty = Iterables.getFirst(pm.get("textures"), null);
                if (textureProperty != null) {
                    String val = textureProperty.value();
                    if (val != null) {
                        TexturesPayload result = null;
                        try {
                            String json = new String(Base64.getDecoder().decode(val), StandardCharsets.UTF_8);
                            result = gson.fromJson(json, TexturesPayload.class);
                        } catch (JsonParseException e) {
                        } catch (IllegalArgumentException x) {
                            Log.warning("Malformed response from skin URL check: " + val);
                        }
                        if ((result != null) && (result.textures != null) && (result.textures.containsKey("SKIN"))) {
                            url = result.textures.get("SKIN").url;
                        }
                    }
                }
            }
        }
        return url;
    }

    // Get minY for world
    @Override
    public int getWorldMinY(World w) {
        CraftWorld cw = (CraftWorld) w;
        return cw.getMinHeight();
    }

    @Override
    public boolean useGenericCache() {
        return true;
    }

}
