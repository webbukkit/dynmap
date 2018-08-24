package org.dynmap.bukkit.helper.v113;

import java.lang.reflect.Field;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Server;
import org.bukkit.World;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitVersionHelperCB;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;
import org.dynmap.bukkit.helper.v113.MapChunkCache113;
import org.dynmap.common.BiomeMap;

import net.minecraft.server.v1_13_R1.BiomeBase;
import net.minecraft.server.v1_13_R1.Block;
import net.minecraft.server.v1_13_R1.BlockFluids;
import net.minecraft.server.v1_13_R1.BlockLogAbstract;
import net.minecraft.server.v1_13_R1.IBlockData;
import net.minecraft.server.v1_13_R1.IBlockState;
import net.minecraft.server.v1_13_R1.Material;

/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperSpigot113 extends BukkitVersionHelperCB {
    
    /** CraftChunkSnapshot */
    protected Class<?> datapalettearray;
    private Field blockid_field;

    public BukkitVersionHelperSpigot113() {
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
        w.unloadChunk(cx, cz, false, false);
    }

    private String stripBlockString(String bname) {
    	int idx = bname.indexOf('{');
    	if (idx >= 0) bname = bname.substring(idx+1);
    	idx = bname.indexOf('}');
    	if (idx >= 0) bname = bname.substring(0, idx);
    	return bname;
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
    		names[i] = Block.REGISTRY.b(bd.getBlock()).b();
    		Log.info(i + ": blk=" + names[i] + ", bd=" + bd.toString());
    	}
        return names;
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
    		String bname = Block.REGISTRY.b(bd.getBlock()).toString();
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
            DynmapBlockState bs = new DynmapBlockState(lastbs, idx, bname, sb);
            if ((!bd.s().e()) && ((bd.getBlock() instanceof BlockFluids) == false)) {	// Test if fluid type for block is not empty
            	bs.setWaterlogged();
            }
            if (bd.getMaterial() == Material.AIR) {
            	bs.setAir();
            }
    		if (bd.getMaterial() == Material.LEAVES) {
    			bs.setLeaves();
    		}
    		if (bd.getBlock() instanceof BlockLogAbstract) {
    			bs.setLog();
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
        MapChunkCache113 c = new MapChunkCache113();
        c.setChunks(dw, chunks);
        return c;
    }
    
	/**
	 * Get biome base water multiplier
	 */
    @Override
	public int getBiomeBaseWaterMult(Object bb) {
		return ((BiomeBase)bb).n();
	}

}
