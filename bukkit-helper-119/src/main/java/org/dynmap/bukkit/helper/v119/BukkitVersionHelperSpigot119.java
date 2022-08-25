package org.dynmap.bukkit.helper.v119;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_19_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.bukkit.helper.BukkitMaterial;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitVersionHelperGeneric.TexturesPayload;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;

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
public class BukkitVersionHelperSpigot119 extends BukkitVersionHelper {
	private final boolean unsafeAsync;

	public BukkitVersionHelperSpigot119() {
		boolean unsafeAsync1;
		try {
			Class.forName("com.destroystokyo.paper.io.PaperFileIOThread");
			unsafeAsync1 = false;
		} catch (ClassNotFoundException e) {
			unsafeAsync1 = true;
		}
		this.unsafeAsync = unsafeAsync1;
	}

	@Override
	public boolean isUnsafeAsync() {
		return unsafeAsync;
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
			ResourceLocation id = DefaultedRegistry.BLOCK.getKey(b);
			String bn = id.toString();
			names.add(bn);
			Log.info("block=" + bn);
		}
		return names.toArray(new String[0]);
	}

	private static Registry<Biome> reg = null;

	private static Registry<Biome> getBiomeReg() {
		if (reg == null) {
			reg = MinecraftServer.getServer().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
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
			for (Biome b : getBiomeReg()) {
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
		dataToState = new IdentityHashMap<>();
		HashMap<String, DynmapBlockState> lastBlockState = new HashMap<String, DynmapBlockState>();
		IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;
		Block baseb = null;
		Iterator<BlockState> iter = bsids.iterator();

		// Loop through block data states
		DynmapBlockState.Builder bld = new DynmapBlockState.Builder();
		while (iter.hasNext()) {
			BlockState bd = iter.next();
			Block b = bd.getBlock();
			ResourceLocation id = DefaultedRegistry.BLOCK.getKey(b);
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
			net.minecraft.world.level.material.Material mat = bd.getMaterial();

			int lightAtten = b.getLightBlock(bd, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			//Log.info("statename=" + bname + "[" + sb + "], lightAtten=" + lightAtten);
			// Fill in base attributes
			bld.setBaseState(lastbs).setStateIndex(idx).setBlockName(bname).setStateName(sb).setMaterial(mat.toString()).setAttenuatesLight(lightAtten);
			if (mat.isSolid()) bld.setSolid();
			if (mat == Material.AIR) bld.setAir();
			if (mat == Material.WOOD) bld.setLog();
			if (mat == Material.LEAVES) bld.setLeaves();
			if (!bd.getFluidState().isEmpty() && !(bd.getBlock() instanceof LiquidBlock)) {    // Test if fluid type for block is not empty
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
		MapChunkCache119 c = new MapChunkCache119(gencache);
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
		return ((Biome) bb).getDownfall();
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
			for (Biome b : getBiomeReg()) {
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

	/** Get ID string from biomebase */
	@Override
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
		return ((CraftChunk) c).getHandle().getInhabitedTime();
	}

	@Override
	public Map<?, ?> getTileEntitiesForChunk(Chunk c) {
		return ((CraftChunk) c).getHandle().blockEntities;
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
	public Object readTileEntityNBT(Object te) {
		BlockEntity tileent = (BlockEntity) te;
		return tileent.saveWithId();
	}

	@Override
	public Object getFieldValue(Object nbt, String field) {
		CompoundTag rec = (CompoundTag) nbt;
		Tag val = rec.get(field);
		if (val == null) return null;
		if (val instanceof ByteTag) {
			return ((ByteTag) val).getAsByte();
		} else if (val instanceof ShortTag) {
			return ((ShortTag) val).getAsShort();
		} else if (val instanceof IntTag) {
			return ((IntTag) val).getAsInt();
		} else if (val instanceof LongTag) {
			return ((LongTag) val).getAsLong();
		} else if (val instanceof FloatTag) {
			return ((FloatTag) val).getAsFloat();
		} else if (val instanceof DoubleTag) {
			return ((DoubleTag) val).getAsDouble();
		} else if (val instanceof ByteArrayTag) {
			return ((ByteArrayTag) val).getAsByteArray();
		} else if (val instanceof StringTag) {
			return val.getAsString();
		} else if (val instanceof IntArrayTag) {
			return ((IntArrayTag) val).getAsIntArray();
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
					String val = textureProperty.getValue();
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
		return w.getMinHeight();
	}

	@Override
	public boolean useGenericCache() {
		return true;
	}

}
