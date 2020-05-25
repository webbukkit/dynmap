package org.dynmap.bukkit.helper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;

/**
 * Helper for isolation of bukkit version specific issues
 */
public abstract class BukkitVersionHelper {
    public static BukkitVersionHelper helper = null;
    
    public static DynmapBlockState[] stateByID;
    
    protected boolean isBlockIdNeeded() {
        return true;
    }
    protected boolean isBiomeBaseListNeeded() {
    	return true;
    }

    protected BukkitVersionHelper() {
        
    }
    /**
     * Get list of defined biomebase objects
     */
    public abstract Object[] getBiomeBaseList();
    /** 
     * Get temperature from biomebase
     */
    public abstract float getBiomeBaseTemperature(Object bb);
    /** 
     * Get humidity from biomebase
     */
    public abstract float getBiomeBaseHumidity(Object bb);
    /**
     * Get ID string from biomebase
     */
    public abstract String getBiomeBaseIDString(Object bb);
    /** 
     * Get ID from biomebase
     */
    public abstract int getBiomeBaseID(Object bb);
    /**
     *  Get unload queue for given NMS world
     */
    public abstract Object getUnloadQueue(World world);
    /**
     *  For testing unload queue for presence of givne chunk
     */
    public abstract boolean isInUnloadQueue(Object unloadqueue, int x, int z);
    /**
     * Read raw biome ID from snapshot
     */
    public abstract Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css);
    /**
     * Test if normal chunk snapshot
     */
//    public abstract boolean isCraftChunkSnapshot(ChunkSnapshot css);
    /**
     * Get inhabited ticks count from chunk
     */
    public abstract long getInhabitedTicks(Chunk c);
    /** 
     * Get tile entities map from chunk
     */
    public abstract Map<?, ?> getTileEntitiesForChunk(Chunk c);
    /**
     * Get X coordinate of tile entity
     */
    public abstract int getTileEntityX(Object te);
    /**
     * Get Y coordinate of tile entity
     */
    public abstract int getTileEntityY(Object te);
    /**
     * Get Z coordinate of tile entity
     */
    public abstract int getTileEntityZ(Object te);
    /**
     * Read tile entity NBT
     */
    public abstract Object readTileEntityNBT(Object te);
    /**
     * Get field value from NBT compound
     */
    public abstract Object getFieldValue(Object nbt, String field);
    /**
     * Unload chunk no save needed
     */
    public abstract void unloadChunkNoSave(World w, Chunk c, int cx, int cz);
    /**
     * Get block name list
     */
    public abstract String[] getBlockNames();
    /**
     * Get biome name list
     */
    public abstract String[] getBiomeNames();
    /**
     * Get list of online players
     */
    public abstract Player[] getOnlinePlayers();
    /**
     * Get player health
     */
    public abstract double getHealth(Player p);
    /**
     * Get world border
     */
    public Polygon getWorldBorder(World world) { return null; }
    /**
     * Test if broken unloadChunk
     */
    public boolean isUnloadChunkBroken() { return false; }
    /**
     * Get skin URL for player
     * @param player
     */
    public String getSkinURL(Player player) { return null; }
    /**
     * Get material map by block ID
     */
    public abstract BukkitMaterial[] getMaterialList();
    /**
     * Initialize block states (org.dynmap.blockstate.DynmapBlockState)
     */
    public void initializeBlockStates() {
        String[] blkname = getBlockNames();
        BukkitMaterial[] blkmat = getMaterialList();
        // Keep it simple for now - just assume 16 meta states for each
        stateByID = new DynmapBlockState[16*blkname.length];
        Arrays.fill(stateByID, DynmapBlockState.AIR);
        for (int i = 0; i < blkname.length; i++) {
        	if (blkname[i] == null) continue;
        	String bn = blkname[i];
        	if (bn.indexOf(':') < 0) {
        		bn = "minecraft:" + bn;
        	}
            // Only do defined names, and not "air"
            if (!bn.equals(DynmapBlockState.AIR_BLOCK)) {
                BukkitMaterial mat = blkmat[i];
                DynmapBlockState basebs = null;
                for (int m = 0; m < 16; m++) {
                	String sn = helper.getStateStringByCombinedId(i, m);
                    DynmapBlockState bs = new DynmapBlockState(basebs, m, bn, sn, mat.name, i);
                    if (basebs == null) basebs = bs;
                    stateByID[(i << 4) + m] = bs;
                    if (mat != null) {
                    	if (mat.name.equals("AIR")) {
                        	bs.setAir();
                    	}
                    	if (mat.name.equals("LEAVES")) {
                        	bs.setLeaves();
                    	}
                    	if (mat.name.equals("WOOD")) {
                        	bs.setLog();
                    	}
                    	if (mat.isSolid) {
                    		bs.setSolid();
                    	}
                    }                    
                }
            }
        }
        //for (int gidx = 0; gidx < DynmapBlockState.getGlobalIndexMax(); gidx++) {
        //	DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(gidx);
        //	Log.verboseinfo(gidx + ":" + bs.toString() + ", gidx=" + bs.globalStateIndex + ", sidx=" + bs.stateIndex);
        //}
    }
    /**
     * Create chunk cache for given chunks of given world
     * @param dw - world
     * @param chunks - chunk list
     * @return cache
     */
    public MapChunkCache getChunkCache(BukkitWorld dw, List<DynmapChunk> chunks) {
        AbstractMapChunkCache c = new MapChunkCacheClassic();
        c.setChunks(dw, chunks);
        return c;
    }
	public Object[] getBlockIDFieldFromSnapshot(ChunkSnapshot css) {
		return null;
	}
	/**
	 * Get biome base water multiplier
	 */
	public int getBiomeBaseWaterMult(Object bb) {
		return -1;
	}

	public abstract String getStateStringByCombinedId(int blkid, int meta);
	
	// Send title/subtitle to user
    public void sendTitleText(Player p, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
    	// Do send message for old implementations
    	if (p != null) {
    		if (title != null) p.sendMessage(title);
    		if (subtitle != null) p.sendMessage(subtitle);
    	}
    }
}