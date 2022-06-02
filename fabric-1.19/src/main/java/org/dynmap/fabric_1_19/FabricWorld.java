package org.dynmap.fabric_1_19;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.dimension.DimensionType;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;

import java.util.List;

public class FabricWorld extends DynmapWorld {
    // TODO: Store this relative to World saves for integrated server
    public static final String SAVED_WORLDS_FILE = "fabricworlds.yml";

    private final DynmapPlugin plugin;
    private World world;
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

    public static String getWorldName(DynmapPlugin plugin, World w) {
        RegistryKey<World> rk = w.getRegistryKey();
        if (rk == World.OVERWORLD) {    // Overworld?
            return w.getServer().getSaveProperties().getLevelName();
        } else if (rk == World.END) {
            return "DIM1";
        } else if (rk == World.NETHER) {
            return "DIM-1";
        } else {
            return rk.getValue().getNamespace() + "_" + rk.getValue().getPath();
        }
    }

    public void updateWorld(World w) {
        this.updateWorldHeights(w.getHeight(), w.getDimension().minY(), w.getSeaLevel());
    }

    public FabricWorld(DynmapPlugin plugin, World w) {
        this(plugin, getWorldName(plugin, w), w.getHeight(),
                w.getSeaLevel(),
                w.getRegistryKey() == World.NETHER,
                w.getRegistryKey() == World.END,
                w.getRegistryKey().getValue().getPath(),
                w.getDimension().minY());
        setWorldLoaded(w);
    }

    public FabricWorld(DynmapPlugin plugin, String name, int height, int sealevel, boolean nether, boolean the_end, String deftitle, int miny) {
        super(name, Math.min(height, maxWorldHeight), sealevel, miny);
        this.plugin = plugin;
        world = null;
        setTitle(deftitle);
        isnether = nether;
        istheend = the_end;
        skylight = !(isnether || istheend);

        if (isnether) {
            env = "nether";
        } else if (istheend) {
            env = "the_end";
        } else {
            env = "normal";
        }

    }

    /* Test if world is nether */
    @Override
    public boolean isNether() {
        return isnether;
    }

    public boolean isTheEnd() {
        return istheend;
    }

    /* Get world spawn location */
    @Override
    public DynmapLocation getSpawnLocation() {
        if (world != null) {
            spawnloc.x = world.getLevelProperties().getSpawnX();
            spawnloc.y = world.getLevelProperties().getSpawnY();
            spawnloc.z = world.getLevelProperties().getSpawnZ();
            spawnloc.world = this.getName();
        }
        return spawnloc;
    }

    /* Get world time */
    @Override
    public long getTime() {
        if (world != null)
            return world.getTimeOfDay();
        else
            return -1;
    }

    /* World is storming */
    @Override
    public boolean hasStorm() {
        if (world != null)
            return world.isRaining();
        else
            return false;
    }

    /* World is thundering */
    @Override
    public boolean isThundering() {
        if (world != null)
            return world.isThundering();
        else
            return false;
    }

    /* World is loaded */
    @Override
    public boolean isLoaded() {
        return (world != null);
    }

    /* Set world to unloaded */
    @Override
    public void setWorldUnloaded() {
        getSpawnLocation();
        world = null;
    }

    /* Set world to loaded */
    public void setWorldLoaded(World w) {
        world = w;
        this.sealevel = w.getSeaLevel();   // Read actual current sealevel from world
        // Update lighting table
        for (int i = 0; i < 16; i++) {
            this.setBrightnessTableEntry(i, FabricWorld.getBrightness(w.getDimension(), i));
        }
    }

    public static float getBrightness(DimensionType type, int lightLevel) {
        float f = (float) lightLevel / 15.0F;
        float g = f / (4.0F - 3.0F * f);
        return MathHelper.lerp(type.ambientLight(), g, 1.0F);
    }

    /* Get light level of block */
    @Override
    public int getLightLevel(int x, int y, int z) {
        if (world != null)
            return world.getLightLevel(new BlockPos(x, y, z));
        else
            return -1;
    }

    /* Get highest Y coord of given location */
    @Override
    public int getHighestBlockYAt(int x, int z) {
        if (world != null) {
            return world.getChunk(x >> 4, z >> 4).getHeightmap(Heightmap.Type.MOTION_BLOCKING).get(x & 15, z & 15);
        } else
            return -1;
    }

    /* Test if sky light level is requestable */
    @Override
    public boolean canGetSkyLightLevel() {
        return skylight;
    }

    /* Return sky light level */
    @Override
    public int getSkyLightLevel(int x, int y, int z) {
        if (world != null) {
            return world.getLightLevel(LightType.SKY, new BlockPos(x, y, z));
        } else
            return -1;
    }

    /**
     * Get world environment ID (lower case - normal, the_end, nether)
     */
    @Override
    public String getEnvironment() {
        return env;
    }

    /**
     * Get map chunk cache for world
     */
    @Override
    public MapChunkCache getChunkCache(List<DynmapChunk> chunks) {
        if (world != null) {
            FabricMapChunkCache c = new FabricMapChunkCache(plugin);
            c.setChunks(this, chunks);
            return c;
        }
        return null;
    }

    public World getWorld() {
        return world;
    }

    @Override
    public Polygon getWorldBorder() {
        if (world != null) {
            WorldBorder wb = world.getWorldBorder();
            if ((wb != null) && (wb.getSize() < 5.9E7)) {
                Polygon p = new Polygon();
                p.addVertex(wb.getBoundWest(), wb.getBoundNorth());
                p.addVertex(wb.getBoundWest(), wb.getBoundSouth());
                p.addVertex(wb.getBoundEast(), wb.getBoundSouth());
                p.addVertex(wb.getBoundEast(), wb.getBoundNorth());
                return p;
            }
        }
        return null;
    }
}
