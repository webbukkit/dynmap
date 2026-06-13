package org.dynmap.bukkit.helper.v261_2;

import org.bukkit.*;
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

import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

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
 * Helper for isolation of bukkit version specific issues - Paper 26.1.x (Mojang mappings)
 */
public class BukkitVersionHelperSpigot261_2 extends BukkitVersionHelper {

	@Override
	public boolean isUnsafeAsync() {
		return false;
	}

	@Override
	public String[] getBlockNames() {
		IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;
		Block baseb = null;
		Iterator<BlockState> iter = bsids.iterator();
		ArrayList<String> names = new ArrayList<String>();
		while (iter.hasNext()) {
			BlockState bs = iter.next();
			Block b = bs.getBlock();
			if (b == baseb) continue;
			baseb = b;
			Identifier id = BuiltInRegistries.BLOCK.getKey(b);
			if (id == null) continue;
			names.add(id.toString());
		}
		return names.toArray(new String[0]);
	}

	private Registry<Biome> reg = null;

	private Registry<Biome> getBiomeReg() {
		if (reg == null) {
			reg = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME);
		}
		return reg;
	}

	private Object[] biomelist;

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

	@Override
	public int getBiomeBaseID(Object bb) {
		return getBiomeReg().getId((Biome) bb);
	}

	public static IdentityHashMap<BlockState, DynmapBlockState> dataToState;

	@Override
	public void initializeBlockStates() {
		dataToState = new IdentityHashMap<BlockState, DynmapBlockState>();
		HashMap<String, DynmapBlockState> lastBlockState = new HashMap<String, DynmapBlockState>();
		IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;
		Iterator<BlockState> iter = bsids.iterator();

		DynmapBlockState.Builder bld = new DynmapBlockState.Builder();
		while (iter.hasNext()) {
			BlockState bd = iter.next();
			Block b = bd.getBlock();
			Identifier id = BuiltInRegistries.BLOCK.getKey(b);
			if (id == null) continue;
			String bname = id.toString();
			DynmapBlockState lastbs = lastBlockState.get(bname);
			int idx = 0;
			if (lastbs != null) {
				idx = lastbs.getStateCount();
			}
			String sb = "";
			String fname = bd.toString();
			int off1 = fname.indexOf('[');
			if (off1 >= 0) {
				int off2 = fname.indexOf(']');
				sb = fname.substring(off1 + 1, off2);
			}
			int lightAtten = bd.getLightDampening();
			bld.setBaseState(lastbs).setStateIndex(idx).setBlockName(bname).setStateName(sb).setAttenuatesLight(lightAtten);
			if (bd.isSolid()) { bld.setSolid(); }
			if (bd.isAir()) { bld.setAir(); }
			if (bd.is(BlockTags.OVERWORLD_NATURAL_LOGS)) { bld.setLog(); }
			if (bd.is(BlockTags.LEAVES)) { bld.setLeaves(); }
			if (!bd.getFluidState().isEmpty() && !(bd.getBlock() instanceof LiquidBlock)) {
				bld.setWaterlogged();
			}
			DynmapBlockState dbs = bld.build();

			dataToState.put(bd, dbs);
			lastBlockState.put(bname, (lastbs == null) ? dbs : lastbs);
			Log.verboseinfo("blk=" + bname + ", idx=" + idx + ", state=" + sb + ", waterlogged=" + dbs.isWaterlogged());
		}
	}

	@Override
	public MapChunkCache getChunkCache(BukkitWorld dw, List<DynmapChunk> chunks) {
		MapChunkCache261_2 c = new MapChunkCache261_2(gencache);
		c.setChunks(dw, chunks);
		return c;
	}

	@Override
	public int getBiomeBaseWaterMult(Object bb) {
		return ((Biome) bb).getWaterColor();
	}

	@Override
	public float getBiomeBaseTemperature(Object bb) {
		return ((Biome) bb).getBaseTemperature();
	}

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

	@Override
	public void sendTitleText(Player p, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
		if (p != null) {
			p.sendTitle(title, subtitle, fadeInTicks, stayTicks, fadeOutTIcks);
		}
	}

	@Override
	public BukkitMaterial[] getMaterialList() {
		return new BukkitMaterial[4096];
	}

	@Override
	public void unloadChunkNoSave(World w, Chunk c, int cx, int cz) {
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
		return null;
	}

	@Override
	public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
		Log.warning("isInUnloadQueue not implemented yet");
		return false;
	}

	@Override
	public Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css) {
		Log.warning("getBiomeBaseFromSnapshot not implemented yet");
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
		return ((BlockEntity) te).getBlockPos().getX();
	}

	@Override
	public int getTileEntityY(Object te) {
		return ((BlockEntity) te).getBlockPos().getY();
	}

	@Override
	public int getTileEntityZ(Object te) {
		return ((BlockEntity) te).getBlockPos().getZ();
	}

	@Override
	public Object readTileEntityNBT(Object te, World w) {
		BlockEntity tileent = (BlockEntity) te;
		CraftWorld cw = (CraftWorld) w;
		return tileent.saveCustomOnly(cw.getHandle().registryAccess());
	}

	@Override
	public Object getFieldValue(Object nbt, String field) {
		CompoundTag rec = (CompoundTag) nbt;
		Tag val = rec.get(field);
		if (val == null) return null;
		if (val instanceof ByteTag bt) return bt.value();
		else if (val instanceof ShortTag st) return st.value();
		else if (val instanceof IntTag it) return it.value();
		else if (val instanceof LongTag lt) return lt.value();
		else if (val instanceof FloatTag ft) return ft.value();
		else if (val instanceof DoubleTag dt) return dt.value();
		else if (val instanceof ByteArrayTag bat) return bat.getAsByteArray();
		else if (val instanceof StringTag st) return st.value();
		else if (val instanceof IntArrayTag iat) return iat.getAsIntArray();
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

	@Override
	public String getSkinURL(Player player) {
		String url = null;
		CraftPlayer cp = (CraftPlayer) player;
		GameProfile profile = cp.getProfile();
		if (profile != null) {
			PropertyMap pm = profile.properties();
			if (pm != null) {
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
