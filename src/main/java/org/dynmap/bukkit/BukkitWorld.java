package org.dynmap.bukkit;
/**
 * Bukkit specific implementation of DynmapWorld
 */
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.utils.MapChunkCache;

public class BukkitWorld extends DynmapWorld {
    private World world;
    private World.Environment env;
    private boolean skylight;
    private DynmapLocation spawnloc = new DynmapLocation();
    
    public BukkitWorld(World w) {
        this(w.getName(), w.getMaxHeight(), w.getSeaLevel(), w.getEnvironment());
        setWorldLoaded(w);
        new Permission("dynmap.world." + getName(), "Dynmap access for world " + getName(), PermissionDefault.OP);
    }
    public BukkitWorld(String name, int height, int sealevel, World.Environment env) {
        super(name, height, sealevel);
        world = null;
        this.env = env;
        skylight = (env == World.Environment.NORMAL);
        new Permission("dynmap.world." + getName(), "Dynmap access for world " + getName(), PermissionDefault.OP);
    }
    /**
     * Set world online
     * @param w - loaded world
     */
    public void setWorldLoaded(World w) {
        world = w;
        env = world.getEnvironment();
        skylight = (env == World.Environment.NORMAL);
    }
    /**
     * Set world unloaded
     */
    @Override
    public void setWorldUnloaded() {
        getSpawnLocation(); /* Remember spawn location before unload */
        world = null;
    }
    /* Test if world is nether */
    @Override
    public boolean isNether() {
        return env == World.Environment.NETHER;
    }
    /* Get world spawn location */
    @Override
    public DynmapLocation getSpawnLocation() {
        if(world != null) {
            Location sloc = world.getSpawnLocation();
            spawnloc.x = sloc.getBlockX();
            spawnloc.y = sloc.getBlockY();
            spawnloc.z = sloc.getBlockZ(); 
            spawnloc.world = normalizeWorldName(sloc.getWorld().getName());
        }
        return spawnloc;
    }
    /* Get world time */
    @Override
    public long getTime() {
        if(world != null) {
            return world.getTime();
        }
        else {
            return -1;
        }
    }
    /* World is storming */
    @Override
    public boolean hasStorm() {
        if(world != null) {
            return world.hasStorm();
        }
        else {
            return false;
        }
    }
    /* World is thundering */
    @Override
    public boolean isThundering() {
        if(world != null) {
            return world.isThundering();
        }
        else {
            return false;
        }
    }
    /* World is loaded */
    @Override
    public boolean isLoaded() {
        return (world != null);
    }
    /* Get light level of block */
    @Override
    public int getLightLevel(int x, int y, int z) {
        if(world != null) {
            return world.getBlockAt(x, y, z).getLightLevel();
        }
        else {
            return -1;
        }
    }
    /* Get highest Y coord of given location */
    @Override
    public int getHighestBlockYAt(int x, int z) {
        if(world != null) {
            return world.getHighestBlockYAt(x, z);
        }
        else {
            return -1;
        }
    }
    /* Test if sky light level is requestable */
    @Override
    public boolean canGetSkyLightLevel() {
        return skylight && (world != null);
    }
    /* Return sky light level */
    @Override
    public int getSkyLightLevel(int x, int y, int z) {
        if(world != null) {
            return world.getBlockAt(x, y, z).getLightFromSky();
        }
        else {
            return -1;
        }
    }
    /**
     * Get world environment ID (lower case - normal, the_end, nether)
     */
    @Override
    public String getEnvironment() {
        return env.name().toLowerCase();
    }
    /**
     * Get map chunk cache for world
     */
    @Override
    public MapChunkCache getChunkCache(List<DynmapChunk> chunks) {
        if(isLoaded()) {
            NewMapChunkCache c = new NewMapChunkCache();
            c.setChunks(this, chunks);
            return c;
        }
        else {
            return null;
        }
    }
    
    public World getWorld() {
        return world;
    }
}
