package org.dynmap.bukkit.helper.v118;

import org.bukkit.*;
import org.bukkit.craftbukkit.v1_18_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
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
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.core.RegistryBlockID;
import net.minecraft.core.RegistryBlocks;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistry;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.nbt.NBTBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.BlockAccessAir;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockFluids;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.IBlockData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperSpigot118 extends BukkitVersionHelper { 
		
    public BukkitVersionHelperSpigot118() {
    	
    }
    
    /**
     * Get block short name list
     */
    @Override
    public String[] getBlockNames() {
    	RegistryBlockID<IBlockData> bsids = Block.p;
        Block baseb = null;
    	Iterator<IBlockData> iter = bsids.iterator();
    	ArrayList<String> names = new ArrayList<String>();
		while (iter.hasNext()) {
			IBlockData bs = iter.next();
            Block b = bs.b();
    		// If this is new block vs last, it's the base block state
    		if (b != baseb) {
                baseb = b;
                continue;
    		}
        	MinecraftKey id = RegistryBlocks.X.b(b);
    		String bn = id.toString();
            if (bn != null) {
            	names.add(bn);
            	System.out.println("block=" + bn);
            }
		}
        return names.toArray(new String[0]);
    }

    private static IRegistry<BiomeBase> reg = null;

    private static IRegistry<BiomeBase> getBiomeReg() {
    	if (reg == null) {
    		reg = MinecraftServer.getServer().aV().d(IRegistry.aR);
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
        	biomelist = new BiomeBase[256];
        	Iterator<BiomeBase> iter = getBiomeReg().iterator();
        	while (iter.hasNext()) {
                BiomeBase b = iter.next();
                int bidx = getBiomeReg().a(b);
        		if (bidx >= biomelist.length) {
        			biomelist = Arrays.copyOf(biomelist, bidx + biomelist.length);
        		}
        		biomelist[bidx] = b;
        	}
        }
        return biomelist;
    }

    /** Get ID from biomebase */
    @Override
    public int getBiomeBaseID(Object bb) {
    	return getBiomeReg().a((BiomeBase)bb);
    }
    
    public static IdentityHashMap<IBlockData, DynmapBlockState> dataToState;
    
    /**
     * Initialize block states (org.dynmap.blockstate.DynmapBlockState)
     */
    @Override
    public void initializeBlockStates() {
    	dataToState = new IdentityHashMap<IBlockData, DynmapBlockState>();
    	HashMap<String, DynmapBlockState> lastBlockState = new HashMap<String, DynmapBlockState>();
    	RegistryBlockID<IBlockData> bsids = Block.p;
        Block baseb = null;
    	Iterator<IBlockData> iter = bsids.iterator();
    	ArrayList<String> names = new ArrayList<String>();
    	
    	// Loop through block data states
    	DynmapBlockState.Builder bld = new DynmapBlockState.Builder();
		while (iter.hasNext()) {
    		IBlockData bd = iter.next();
    		Block b = bd.b();
        	MinecraftKey id = RegistryBlocks.X.b(b);
    		String bname = id.toString();
    		DynmapBlockState lastbs = lastBlockState.get(bname);	// See if we have seen this one
    		int idx = 0;
    		if (lastbs != null) {	// Yes
    			idx = lastbs.getStateCount();	// Get number of states so far, since this is next
    		}
    		// Build state name
    		String sb = "";
    		String fname = bd.toString();
    		int off1 = fname.indexOf('[');
    		if (off1 >= 0) {
    			int off2 = fname.indexOf(']');
    			sb = fname.substring(off1+1, off2);
    		}
    		net.minecraft.world.level.material.Material mat = bd.c();
    		
            int lightAtten = b.g(bd, BlockAccessAir.a, BlockPosition.b);	// getLightBlock
            //Log.info("statename=" + bname + "[" + sb + "], lightAtten=" + lightAtten);
            // Fill in base attributes
            bld.setBaseState(lastbs).setStateIndex(idx).setBlockName(bname).setStateName(sb).setMaterial(mat.toString()).setAttenuatesLight(lightAtten);
    		if (mat.b()) { bld.setSolid(); }
            if (mat == net.minecraft.world.level.material.Material.a) { bld.setAir(); }
            if (mat == net.minecraft.world.level.material.Material.z) { bld.setLog(); }
            if (mat == net.minecraft.world.level.material.Material.F) { bld.setLeaves(); }
            if ((!bd.n().c()) && ((bd.b() instanceof BlockFluids) == false)) {	// Test if fluid type for block is not empty
				bld.setWaterlogged();
			}
            DynmapBlockState dbs = bld.build(); // Build state
            
    		dataToState.put(bd,  dbs);
    		lastBlockState.put(bname, (lastbs == null) ? dbs : lastbs);
    		Log.verboseinfo("blk=" + bname + ", idx=" + idx + ", state=" + sb + ", waterlogged=" + dbs.isWaterlogged());
    	}
    }
    /**
     * Create chunk cache for given chunks of given world
     * @param dw - world
     * @param chunks - chunk list
     * @return cache
     */
    @Override
    public MapChunkCache getChunkCache(BukkitWorld dw, List<DynmapChunk> chunks) {
        MapChunkCache118 c = new MapChunkCache118(gencache);
        c.setChunks(dw, chunks);
        return c;
    }
    
	/**
	 * Get biome base water multiplier
	 */
    @Override
	public int getBiomeBaseWaterMult(Object bb) {
    	BiomeBase biome = (BiomeBase) bb;
    	return biome.k();	// waterColor
	}

    /** Get temperature from biomebase */
    @Override
    public float getBiomeBaseTemperature(Object bb) {
    	return ((BiomeBase)bb).i();
    }

    /** Get humidity from biomebase */
    @Override
    public float getBiomeBaseHumidity(Object bb) {
    	return ((BiomeBase)bb).h(); 	
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
        		p.addVertex(c.getX()-size, c.getZ()-size);
        		p.addVertex(c.getX()+size, c.getZ()-size);
        		p.addVertex(c.getX()+size, c.getZ()+size);
        		p.addVertex(c.getX()-size, c.getZ()+size);
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
    	return new BukkitMaterial[4096];	// Not used
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
        	Iterator<BiomeBase> iter = getBiomeReg().iterator();
        	while (iter.hasNext()) {
                BiomeBase b = iter.next();
                int bidx = getBiomeReg().a(b);
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
		return getBiomeReg().b((BiomeBase)bb).a();
    }
	@Override
    public String getBiomeBaseResourceLocsation(Object bb) {
        return getBiomeReg().b((BiomeBase)bb).toString();
	}

	@Override
	public Object getUnloadQueue(World world) {
		System.out.println("getUnloadQueue not implemented yet");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
		System.out.println("isInUnloadQueue not implemented yet");
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css) {
		System.out.println("getBiomeBaseFromSnapshot not implemented yet");
		// TODO Auto-generated method stub
		return new Object[256];
	}

	@Override
	public long getInhabitedTicks(Chunk c) {
		return ((CraftChunk)c).getHandle().u();
	}

	@Override
	public Map<?, ?> getTileEntitiesForChunk(Chunk c) {
		return ((CraftChunk)c).getHandle().i;
	}

	@Override
	public int getTileEntityX(Object te) {
		TileEntity tileent = (TileEntity) te;
		return tileent.p().u();
	}

	@Override
	public int getTileEntityY(Object te) {
		TileEntity tileent = (TileEntity) te;
		return tileent.p().v();
	}

	@Override
	public int getTileEntityZ(Object te) {
		TileEntity tileent = (TileEntity) te;
		return tileent.p().w();
	}

	@Override
	public Object readTileEntityNBT(Object te) {
		TileEntity tileent = (TileEntity) te;
		NBTTagCompound nbt = tileent.n();
        return nbt;
	}

	@Override
	public Object getFieldValue(Object nbt, String field) {
		NBTTagCompound rec = (NBTTagCompound) nbt;
		NBTBase val = rec.c(field);
        if(val == null) return null;
        if(val instanceof NBTTagByte) {
            return ((NBTTagByte)val).h();
        }
        else if(val instanceof NBTTagShort) {
            return ((NBTTagShort)val).g();
        }
        else if(val instanceof NBTTagInt) {
            return ((NBTTagInt)val).f();
        }
        else if(val instanceof NBTTagLong) {
            return ((NBTTagLong)val).e();
        }
        else if(val instanceof NBTTagFloat) {
            return ((NBTTagFloat)val).j();
        }
        else if(val instanceof NBTTagDouble) {
            return ((NBTTagDouble)val).i();
        }
        else if(val instanceof NBTTagByteArray) {
            return ((NBTTagByteArray)val).d();
        }
        else if(val instanceof NBTTagString) {
            return ((NBTTagString)val).e_();
        }
        else if(val instanceof NBTTagIntArray) {
            return ((NBTTagIntArray)val).f();
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
     * @param player
     */
	@Override
    public String getSkinURL(Player player) {
    	String url = null;
    	CraftPlayer cp = (CraftPlayer)player;
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
    						String json = new String(Base64Coder.decode(val), Charsets.UTF_8);
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
