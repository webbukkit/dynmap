package org.dynmap.forge_1_17_1;
/**
 * Forge specific implementation of DynmapWorld
 */
import java.util.List;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;

public class ForgeWorld extends DynmapWorld
{
    private ServerLevelAccessor world;
    private final boolean skylight;
    private final boolean isnether;
    private final boolean istheend;
    private final String env;
    private DynmapLocation spawnloc = new DynmapLocation();
    private static int maxWorldHeight = 320;    // Maximum allows world height
    
    public static int getMaxWorldHeight() {
        return maxWorldHeight;
    }
    public static void setMaxWorldHeight(int h) {
        maxWorldHeight = h;
    }

    public static String getWorldName(ServerLevelAccessor w) {
        ResourceKey<Level> rk = w.getLevel().dimension();
        if (rk == Level.OVERWORLD) {    // Overworld?
            return w.getLevel().serverLevelData.getLevelName();
        } else if (rk == Level.END) {
            return "DIM1";
        } else if (rk == Level.NETHER) {
            return "DIM-1";
        } else {
            return rk.getRegistryName() + "_" + rk.location();
        }
    }

    public void updateWorld(ServerLevelAccessor w) {
    	this.updateWorldHeights(w.getLevel().getHeight(), w.getLevel().dimensionType().minY(), w.getLevel().getSeaLevel());
    }

    public ForgeWorld(ServerLevelAccessor w)
    {
        this(getWorldName(w), 
        	w.getLevel().getHeight(), 
    		w.getLevel().getSeaLevel(), 
    		w.getLevel().dimension() == Level.NETHER,
    		w.getLevel().dimension() == Level.END,
			getWorldName(w),
			w.getLevel().dimensionType().minY());
        setWorldLoaded(w);
    }
    public ForgeWorld(String name, int height, int sealevel, boolean nether, boolean the_end, String deftitle, int miny)
    {
        super(name, (height > maxWorldHeight)?maxWorldHeight:height, sealevel, miny);
        world = null;
        setTitle(deftitle);
        isnether = nether;
        istheend = the_end;
        skylight = !(isnether || istheend);

        if (isnether)
        {
            env = "nether";
        }
        else if (istheend)
        {
            env = "the_end";
        }
        else
        {
            env = "normal";
        }
        //Log.info(getName() + ": skylight=" + skylight + ", height=" + this.worldheight + ", isnether=" + isnether + ", istheend=" + istheend);
    }
    /* Test if world is nether */
    @Override
    public boolean isNether()
    {
        return isnether;
    }
    public boolean isTheEnd()
    {
        return istheend;
    }
    /* Get world spawn location */
    @Override
    public DynmapLocation getSpawnLocation()
    {
    	if(world != null) {
    		BlockPos p = world.getLevel().getSharedSpawnPos();
    		spawnloc.x = p.getX();
    		spawnloc.y = p.getY();
    		spawnloc.z = p.getZ();
    		spawnloc.world = this.getName();
    	}
        return spawnloc;
    }
    /* Get world time */
    @Override
    public long getTime()
    {
    	if(world != null)
    		return world.getLevel().getDayTime();
    	else
    		return -1;
    }
    /* World is storming */
    @Override
    public boolean hasStorm()
    {
    	if(world != null)
    		return world.getLevel().isRaining();
    	else
    		return false;
    }
    /* World is thundering */
    @Override
    public boolean isThundering()
    {
    	if(world != null)
    		return world.getLevel().isThundering();
    	else
    		return false;
    }
    /* World is loaded */
    @Override
    public boolean isLoaded()
    {
        return (world != null);
    }
    /* Set world to unloaded */
    @Override
    public void setWorldUnloaded() 
    {
    	getSpawnLocation();
    	world = null;
    }
    /* Set world to loaded */
    public void setWorldLoaded(ServerLevelAccessor w) {
    	world = w;
    	this.sealevel = w.getLevel().getSeaLevel();   // Read actual current sealevel from world
    	// Update lighting table
    	for (int i = 0; i < 16; i++) {
    		float light = w.getLevel().dimensionType().brightness(i);
    	    this.setBrightnessTableEntry(i, light);
    	    //Log.info(getName() + ": light " + i + " = " + light);
    	}
    }
    /* Get light level of block */
    @Override
    public int getLightLevel(int x, int y, int z)
    {
    	if(world != null)
    		return world.getLevel().getLightEngine().getRawBrightness(new BlockPos(x,  y,  z), 0);
    	else
    		return -1;
    }
    /* Get highest Y coord of given location */
    @Override
    public int getHighestBlockYAt(int x, int z)
    {
    	if(world != null) {
            return world.getLevel().getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
    	}
    	else
    		return -1;
    }
    /* Test if sky light level is requestable */
    @Override
    public boolean canGetSkyLightLevel()
    {
        return skylight;
    }
    /* Return sky light level */
    @Override
    public int getSkyLightLevel(int x, int y, int z)
    {
    	if(world != null) {
    		return world.getLevel().getBrightness(LightLayer.SKY, new BlockPos(x, y, z));
    	}
    	else
    		return -1;
    }
    /**
     * Get world environment ID (lower case - normal, the_end, nether)
     */
    @Override
    public String getEnvironment()
    {
        return env;
    }
    /**
     * Get map chunk cache for world
     */
    @Override
    public MapChunkCache getChunkCache(List<DynmapChunk> chunks)
    {
    	if(world != null) {
    		ForgeMapChunkCache c = new ForgeMapChunkCache();
    		c.setChunks(this, chunks);
    		return c;
    	}
    	return null;
    }

    public ServerLevel getWorld()
    {
        return world.getLevel();
    }
    @Override
    public Polygon getWorldBorder() {
        if (world != null) {
            WorldBorder wb = world.getWorldBorder();
            if ((wb != null) && (wb.getSize() < 5.9E7)) {
                Polygon p = new Polygon();
                p.addVertex(wb.getMinX(), wb.getMinZ());
                p.addVertex(wb.getMinX(), wb.getMaxZ());
                p.addVertex(wb.getMaxX(), wb.getMaxZ());
                p.addVertex(wb.getMaxX(), wb.getMinZ());
                return p;
            }
        }
        return null;
    }
}
