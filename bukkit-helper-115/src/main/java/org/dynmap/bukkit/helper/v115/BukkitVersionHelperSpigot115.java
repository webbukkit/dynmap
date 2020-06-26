package org.dynmap.bukkit.helper.v115;

import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.bukkit.helper.BukkitVersionHelperCB;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.bukkit.helper.v115.MapChunkCache115;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;

import net.minecraft.server.v1_15_R1.BiomeBase;
import net.minecraft.server.v1_15_R1.Block;
import net.minecraft.server.v1_15_R1.BlockFluids;
import net.minecraft.server.v1_15_R1.BlockLogAbstract;
import net.minecraft.server.v1_15_R1.IBlockData;
import net.minecraft.server.v1_15_R1.IRegistry;
import net.minecraft.server.v1_15_R1.Material;

/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperSpigot115 extends BukkitVersionHelperCB {
    
    /** CraftChunkSnapshot */
    protected Class<?> datapalettearray;
    private Field blockid_field;

    @Override
    protected boolean isBlockIdNeeded() {
        return false;
    }
    
    @Override
    protected boolean isBiomeBaseListNeeded() {
    	return false;
    }

    public BukkitVersionHelperSpigot115() {
		datapalettearray =  getNMSClass("[Lnet.minecraft.server.DataPaletteBlock;");
    	blockid_field = getPrivateField(craftchunksnapshot, new String[] { "blockids" }, datapalettearray);
    }
    
    @Override
    public Object[] getBlockIDFieldFromSnapshot(ChunkSnapshot css) {
    	try {
			return (Object[]) blockid_field.get(css);
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		}
    	return null;
    }
    @Override
    public void unloadChunkNoSave(World w, Chunk c, int cx, int cz) {
        w.unloadChunk(cx, cz, false);
    }

    /**
     * Get block short name list
     */
    @Override
    public String[] getBlockNames() {
    	int cnt = Block.REGISTRY_ID.a();
    	String[] names = new String[cnt];
    	for (int i = 0; i < cnt; i++) {
    		IBlockData bd = Block.getByCombinedId(i);
    		names[i] = IRegistry.BLOCK.getKey(bd.getBlock()).toString();
    		Log.info(i + ": blk=" + names[i] + ", bd=" + bd.toString());
    	}
        return names;
    }
    
    /**
     * Get list of defined biomebase objects
     */
    @Override
    public Object[] getBiomeBaseList() {
    	if (biomelist == null) {
    		biomelist = new Object[1024];
            for (int i = 0; i < 1024; i++) {
            	biomelist[i] = IRegistry.BIOME.fromId(i);
            }
        }
        return biomelist;
    }

    /** Get ID from biomebase */
    @Override
    public int getBiomeBaseID(Object bb) {
        return IRegistry.BIOME.a((BiomeBase)bb);
    }
    
    public static IdentityHashMap<IBlockData, DynmapBlockState> dataToState;
    
    /**
     * Initialize block states (org.dynmap.blockstate.DynmapBlockState)
     */
    @Override
    public void initializeBlockStates() {
    	dataToState = new IdentityHashMap<IBlockData, DynmapBlockState>();
    	HashMap<String, DynmapBlockState> lastBlockState = new HashMap<String, DynmapBlockState>();
    	
    	int cnt = Block.REGISTRY_ID.a();
    	// Loop through block data states
    	for (int i = 0; i < cnt; i++) {
    		IBlockData bd = Block.getByCombinedId(i);
    		String bname = IRegistry.BLOCK.getKey(bd.getBlock()).toString();
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
    		Material mat = bd.getMaterial();
            DynmapBlockState bs = new DynmapBlockState(lastbs, idx, bname, sb, mat.toString());
            if ((!bd.getFluid().isEmpty()) && ((bd.getBlock() instanceof BlockFluids) == false)) {	// Test if fluid type for block is not empty
            	bs.setWaterlogged();
            }
            if (mat == Material.AIR) {
            	bs.setAir();
            }
    		if (mat == Material.LEAVES) {
    			bs.setLeaves();
    		}
    		if (bd.getBlock() instanceof BlockLogAbstract) {
    			bs.setLog();
    		}
    		if (mat.isSolid()) {
    			bs.setSolid();
    		}
    		dataToState.put(bd,  bs);
    		lastBlockState.put(bname, (lastbs == null) ? bs : lastbs);
    		Log.verboseinfo(i + ": blk=" + bname + ", idx=" + idx + ", state=" + sb + ", waterlogged=" + bs.isWaterlogged());
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
        MapChunkCache115 c = new MapChunkCache115();
        c.setChunks(dw, chunks);
        return c;
    }
    
	/**
	 * Get biome base water multiplier
	 */
    @Override
	public int getBiomeBaseWaterMult(Object bb) {
		return ((BiomeBase)bb).o();
	}

    /** Get temperature from biomebase */
    @Override
    public float getBiomeBaseTemperature(Object bb) {
    	return ((BiomeBase)bb).getTemperature();
    }

    /** Get humidity from biomebase */
    @Override
    public float getBiomeBaseHumidity(Object bb) {
    	return ((BiomeBase)bb).getHumidity();    	
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
	private String[] biomenames;
	@Override
	public String[] getBiomeNames() {
    	if (biomenames == null) {
    		biomenames = new String[1024];
            for (int i = 0; i < 1024; i++) {
            	BiomeBase bb = IRegistry.BIOME.fromId(i);
            	if (bb != null) {
            		biomenames[i] = bb.t();
            	}
            }
        }
        return biomenames;
	}

}
