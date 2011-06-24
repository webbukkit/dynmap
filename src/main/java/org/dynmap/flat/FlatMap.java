package org.dynmap.flat;

import org.dynmap.DynmapWorld;
import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.dynmap.Client;
import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.MapManager;
import org.dynmap.TileHashManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.debug.Debug;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.kzedmap.KzedMap.KzedBufferedImage;
import org.dynmap.utils.FileLockManager;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.json.simple.JSONObject;

public class FlatMap extends MapType {
    private ConfigurationNode configuration;
    private String prefix;
    private ColorScheme colorScheme;
    private int maximumHeight = 127;
    private int ambientlight = 15;;
    private int shadowscale[] = null;
    private boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */
    protected boolean transparency;
    private enum Texture { NONE, SMOOTH, DITHER };
    private Texture textured = Texture.NONE;
    
    public FlatMap(ConfigurationNode configuration) {
        this.configuration = configuration;
        prefix = (String) configuration.get("prefix");
        colorScheme = ColorScheme.getScheme((String) configuration.get("colorscheme"));
        Object o = configuration.get("maximumheight");
        if (o != null) {
            maximumHeight = Integer.parseInt(String.valueOf(o));
            if (maximumHeight > 127)
                maximumHeight = 127;
        }
        o = configuration.get("shadowstrength");
        if(o != null) {
            double shadowweight = Double.parseDouble(String.valueOf(o));
            if(shadowweight > 0.0) {
                shadowscale = new int[16];
                shadowscale[15] = 256;
                /* Normal brightness weight in MC is a 20% relative dropoff per step */
                for(int i = 14; i >= 0; i--) {
                    double v = shadowscale[i+1] * (1.0 - (0.2 * shadowweight));
                    shadowscale[i] = (int)v;
                    if(shadowscale[i] > 256) shadowscale[i] = 256;
                    if(shadowscale[i] < 0) shadowscale[i] = 0;
                }
            }
        }
        o = configuration.get("ambientlight");
        if(o != null) {
            ambientlight = Integer.parseInt(String.valueOf(o));
        }
        night_and_day = configuration.getBoolean("night-and-day", false);
        transparency = configuration.getBoolean("transparency", false);  /* Default off */
        String tex = configuration.getString("textured", "none");
        if(tex.equals("none"))
            textured = Texture.NONE;
        else if(tex.equals("dither"))
            textured = Texture.DITHER;
        else
            textured = Texture.SMOOTH;
    }

    @Override
    public MapTile[] getTiles(Location l) {
        DynmapWorld w = MapManager.mapman.getWorld(l.getWorld().getName());
        return new MapTile[] { new FlatMapTile(w, this, (int) Math.floor(l.getBlockX() / 128.0), (int) Math.floor(l.getBlockZ() / 128.0), 128) };
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        FlatMapTile t = (FlatMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.x;
        int y = t.y;
        int s = t.size;
        return new MapTile[] {
            new FlatMapTile(w, this, x, y - 1, s),
            new FlatMapTile(w, this, x + 1, y, s),
            new FlatMapTile(w, this, x, y + 1, s),
            new FlatMapTile(w, this, x - 1, y, s) };
    }

    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        FlatMapTile t = (FlatMapTile) tile;
        int chunksPerTile = t.size / 16;
        int sx = t.x * chunksPerTile;
        int sz = t.y * chunksPerTile;

        ArrayList<DynmapChunk> result = new ArrayList<DynmapChunk>(chunksPerTile * chunksPerTile);
        for (int x = 0; x < chunksPerTile; x++)
            for (int z = 0; z < chunksPerTile; z++) {
                result.add(new DynmapChunk(sx + x, sz + z));
            }
        return result;
    }

    @Override
    public boolean isHightestBlockYDataNeeded() {
        return true;
    }

    @Override
    public boolean render(MapChunkCache cache, MapTile tile, File outputFile) {
        FlatMapTile t = (FlatMapTile) tile;
        World w = t.getWorld();
        boolean isnether = (w.getEnvironment() == Environment.NETHER) && (maximumHeight == 127);

        boolean rendered = false;
        Color rslt = new Color();
        int[] pixel = new int[4];
        int[] pixel_day = null;
        KzedBufferedImage im = KzedMap.allocateBufferedImage(t.size, t.size);
        int[] argb_buf = im.argb_buf;
        KzedBufferedImage im_day = null;
        int[] argb_buf_day = null;
        if(night_and_day) {
            im_day = KzedMap.allocateBufferedImage(t.size, t.size);
            argb_buf_day = im_day.argb_buf;
            pixel_day = new int[4];
        }
        MapIterator mapiter = cache.getIterator(t.x * t.size, 127, t.y * t.size);
        for (int x = 0; x < t.size; x++) {
            mapiter.initialize(t.x * t.size + x, 127, t.y * t.size);
            for (int y = 0; y < t.size; y++, mapiter.incrementZ()) {
                int blockType;
                mapiter.setY(127);
                if(isnether) {
                    while((blockType = mapiter.getBlockTypeID()) != 0) {
                        mapiter.decrementY();
                        if(mapiter.getY() < 0) {    /* Solid - use top */
                            mapiter.setY(127);
                            blockType = mapiter.getBlockTypeID();
                            break;
                        }
                    }
                    if(blockType == 0) {    /* Hit air - now find non-air */
                        while((blockType = mapiter.getBlockTypeID()) == 0) {
                            mapiter.decrementY();
                            if(mapiter.getY() < 0) {
                                mapiter.setY(0);
                                break;
                            }
                        }
                    }
                }
                else {
                    int my = mapiter.getHighestBlockYAt();
                    if(my > maximumHeight) my = maximumHeight;
                    mapiter.setY(my);
                    blockType = mapiter.getBlockTypeID();
                    if(blockType == 0) {    /* If air, go down one - fixes ice */
                        my--;
                        if(my < 0)
                            continue;
                        mapiter.setY(my);
                        blockType = mapiter.getBlockTypeID();
                    }
                }
                int data = 0;
                Color[] colors = colorScheme.colors[blockType];
                if(colorScheme.datacolors[blockType] != null) {
                    data = mapiter.getBlockData();
                    colors = colorScheme.datacolors[blockType][data];
                }
                if (colors == null)
                    continue;
                Color c;
                if(textured == Texture.SMOOTH)
                    c = colors[4];
                else if((textured == Texture.DITHER) && (((x+y) & 0x01) == 1)) {
                    c = colors[2];                    
                }
                else {
                    c = colors[0];
                }
                if (c == null)
                    continue;

                pixel[0] = c.getRed();
                pixel[1] = c.getGreen();
                pixel[2] = c.getBlue();
                pixel[3] = c.getAlpha();
                
                /* If transparency needed, process it */
                if(transparency && (pixel[3] < 255)) {
                    process_transparent(pixel, pixel_day, mapiter);
                }
                /* If ambient light less than 15, do scaling */
                else if((shadowscale != null) && (ambientlight < 15)) {
                    if(mapiter.getY() < 127) 
                        mapiter.incrementY();
                    if(night_and_day) { /* Use unscaled color for day (no shadows from above) */
                        pixel_day[0] = pixel[0];    
                        pixel_day[1] = pixel[1];
                        pixel_day[2] = pixel[2];
                        pixel_day[3] = 255;
                    }
                    int light = Math.max(ambientlight, mapiter.getBlockEmittedLight());
                    pixel[0] = (pixel[0] * shadowscale[light]) >> 8;
                    pixel[1] = (pixel[1] * shadowscale[light]) >> 8;
                    pixel[2] = (pixel[2] * shadowscale[light]) >> 8;
                    pixel[3] = 255;
                }
                else {  /* Only do height keying if we're not messing with ambient light */
                    boolean below = mapiter.getY() < 64;

                    // Make height range from 0 - 1 (1 - 0 for below and 0 - 1 above)
                    float height = (below ? 64 - mapiter.getY() : mapiter.getY() - 64) / 64.0f;

                    // Defines the 'step' in coloring.
                    float step = 10 / 128.0f;

                    // The step applied to height.
                    float scale = ((int)(height/step))*step;

                    // Make the smaller values change the color (slightly) more than the higher values.
                    scale = (float)Math.pow(scale, 1.1f);

                    // Don't let the color go fully white or fully black.
                    scale *= 0.8f;

                    if (below) {
                        pixel[0] -= pixel[0] * scale;
                        pixel[1] -= pixel[1] * scale;
                        pixel[2] -= pixel[2] * scale;
                        pixel[3] = 255;
                    } else {
                        pixel[0] += (255-pixel[0]) * scale;
                        pixel[1] += (255-pixel[1]) * scale;
                        pixel[2] += (255-pixel[2]) * scale;
                        pixel[3] = 255;
                    }
                    if(night_and_day) {
                        pixel_day[0] = pixel[0];
                        pixel_day[1] = pixel[1];
                        pixel_day[2] = pixel[2];
                        pixel_day[3] = 255;
                    }
                        
                }
                rslt.setRGBA(pixel[0], pixel[1], pixel[2], pixel[3]);
                argb_buf[(t.size-y-1) + (x*t.size)] = rslt.getARGB();
                if(night_and_day) {
                    rslt.setRGBA(pixel_day[0], pixel_day[1], pixel_day[2], pixel[3]);
                    argb_buf_day[(t.size-y-1) + (x*t.size)] = rslt.getARGB();
                }
                rendered = true;
            }
        }
        /* Test to see if we're unchanged from older tile */
        FileLockManager.getWriteLock(outputFile);
        TileHashManager hashman = MapManager.mapman.hashman;
        long crc = hashman.calculateTileHash(argb_buf);
        boolean tile_update = false;
        if((!outputFile.exists()) || (crc != hashman.getImageHashCode(tile.getKey(), null, t.x, t.y))) {
            /* Wrap buffer as buffered image */
            Debug.debug("saving image " + outputFile.getPath());
            if(!outputFile.getParentFile().exists())
                outputFile.getParentFile().mkdirs();
            try {
                FileLockManager.imageIOWrite(im.buf_img, "png", outputFile);
            } catch (IOException e) {
                Debug.error("Failed to save image: " + outputFile.getPath(), e);
            } catch (java.lang.NullPointerException e) {
                Debug.error("Failed to save image (NullPointerException): " + outputFile.getPath(), e);
            }
            MapManager.mapman.pushUpdate(tile.getWorld(), new Client.Tile(tile.getFilename()));
            hashman.updateHashCode(tile.getKey(), null, t.x, t.y, crc);
            tile.getDynmapWorld().enqueueZoomOutUpdate(outputFile);
            tile_update = true;
        }
        else {
            Debug.debug("skipping image " + outputFile.getPath() + " - hash match");
        }
        KzedMap.freeBufferedImage(im);
        FileLockManager.releaseWriteLock(outputFile);
        MapManager.mapman.updateStatistics(tile, null, true, tile_update, !rendered);

        /* If day too, handle it */
        if(night_and_day) {
            File dayfile = new File(tile.getDynmapWorld().worldtilepath, tile.getDayFilename());
            FileLockManager.getWriteLock(dayfile);
            crc = hashman.calculateTileHash(argb_buf_day);
            if((!dayfile.exists()) || (crc != hashman.getImageHashCode(tile.getKey(), "day", t.x, t.y))) {
                Debug.debug("saving image " + dayfile.getPath());
                if(!dayfile.getParentFile().exists())
                    dayfile.getParentFile().mkdirs();
                try {
                    FileLockManager.imageIOWrite(im_day.buf_img, "png", dayfile);
                } catch (IOException e) {
                    Debug.error("Failed to save image: " + dayfile.getPath(), e);
                } catch (java.lang.NullPointerException e) {
                    Debug.error("Failed to save image (NullPointerException): " + dayfile.getPath(), e);
                }
                MapManager.mapman.pushUpdate(tile.getWorld(), new Client.Tile(tile.getDayFilename()));   
                hashman.updateHashCode(tile.getKey(), "day", t.x, t.y, crc);
                tile.getDynmapWorld().enqueueZoomOutUpdate(dayfile);
                tile_update = true;
            }
            else {
                Debug.debug("skipping image " + dayfile.getPath() + " - hash match");
                tile_update = false;
            }
            KzedMap.freeBufferedImage(im_day);
            FileLockManager.releaseWriteLock(dayfile);
            MapManager.mapman.updateStatistics(tile, "day", true, tile_update, !rendered);
        }
        
        return rendered;
    }
    private void process_transparent(int[] pixel, int[] pixel_day, MapIterator mapiter) {
        int r = pixel[0], g = pixel[1], b = pixel[2], a = pixel[3];
        int r_day = 0, g_day = 0, b_day = 0, a_day = 0;
        if(pixel_day != null) {
            r_day = pixel[0]; g_day = pixel[1]; b_day = pixel[2]; a_day = pixel[3];
        }
        /* Scale alpha to be proportional to iso view (where we go through 4 blocks to go sqrt(6) or 2.45 units of distance */
        if(a < 255)
            a = a_day = 255 - ((255-a)*(255-a) >> 8);
        /* Handle lighting on cube */
        if((shadowscale != null) && (ambientlight < 15)) {
            boolean did_inc = false;
            if(mapiter.getY() < 127) {
                mapiter.incrementY();
                did_inc = true;
            }
            if(night_and_day) { /* Use unscaled color for day (no shadows from above) */
                r_day = r; g_day = g; b_day = b; a_day = a;
            }
            int light = Math.max(ambientlight, mapiter.getBlockEmittedLight());
            r = (r * shadowscale[light]) >> 8;
            g = (g * shadowscale[light]) >> 8;
            b = (b * shadowscale[light]) >> 8;
            if(did_inc)
                mapiter.decrementY();
        }
        if(a < 255) {   /* If not opaque */
            pixel[0] = pixel[1] = pixel[2] = pixel[3] = 0;
            if(pixel_day != null) 
                pixel_day[0] = pixel_day[1] = pixel_day[2] = pixel_day[3] = 0;
            mapiter.decrementY();
            if(mapiter.getY() >= 0) {
                int blockType = mapiter.getBlockTypeID();
                int data = 0;
                Color[] colors = colorScheme.colors[blockType];
                if(colorScheme.datacolors[blockType] != null) {
                    data = mapiter.getBlockData();
                    colors = colorScheme.datacolors[blockType][data];
                }
                if (colors != null) {
                    Color c = colors[0];
                    if (c != null) {
                        pixel[0] = c.getRed();
                        pixel[1] = c.getGreen();
                        pixel[2] = c.getBlue();
                        pixel[3] = c.getAlpha();
                    }
                }
                /* Recurse to resolve color here */
                process_transparent(pixel, pixel_day, mapiter);
            }
        }
        /* Blend colors from behind block and block, based on alpha */
        r *= a;
        g *= a;
        b *= a;
        int na = 255 - a;
        pixel[0] = (pixel[0] * na + r) >> 8;
        pixel[1] = (pixel[1] * na + g) >> 8;
        pixel[2] = (pixel[2] * na + b) >> 8;
        pixel[3] = 255;
        if(pixel_day != null) {
            r_day *= a_day;
            g_day *= a_day;
            b_day *= a_day;
            na = 255 - a_day;
            pixel_day[0] = (pixel_day[0] * na + r_day) >> 8;
            pixel_day[1] = (pixel_day[1] * na + g_day) >> 8;
            pixel_day[2] = (pixel_day[2] * na + b_day) >> 8;
            pixel_day[3] = 255;
        }
    }

    public String getName() {
        return prefix;
    }

    public List<String> baseZoomFilePrefixes() {
        ArrayList<String> s = new ArrayList<String>();
        s.add(getName() + "_128");
        if(night_and_day)
            s.add(getName()+"_day_128");
        return s;
    }
    
    public int baseZoomFileStepSize() { return 1; }

    private static final int[] stepseq = { 1, 3, 0, 2 };
    
    public int[] zoomFileStepSequence() { return stepseq; }

    /* How many bits of coordinate are shifted off to make big world directory name */
    public int getBigWorldShift() { return 5; }

    public static class FlatMapTile extends MapTile {
        FlatMap map;
        public int x;
        public int y;
        public int size;
        private String fname;
        private String fname_day;

        public FlatMapTile(DynmapWorld world, FlatMap map, int x, int y, int size) {
            super(world, map);
            this.map = map;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        @Override
        public String getFilename() {
            if(fname == null) {
                if(world.bigworld)
                    fname = map.prefix + "_" + size + "/" + ((-(y+1))>>5) + "_" + (x>>5) + "/" + -(y+1) + "_" + x + ".png";
                else
                    fname = map.prefix + "_" + size + "_" + -(y+1) + "_" + x + ".png";
            }
            return fname;
        }
        @Override
        public String getDayFilename() {
            if(fname_day == null) {
                if(world.bigworld)
                    fname_day = map.prefix + "_day_" + size + "/" + ((-(y+1))>>5) + "_" + (x>>5) + "/" + -(y+1) + "_" + x + ".png";
                else
                    fname_day = map.prefix + "_day_" + size + "_" + -(y+1) + "_" + x + ".png";
            }
            return fname_day;
        }
        public String toString() {
            return getWorld().getName() + ":" + getFilename();
        }
    }
    
    @Override
    public void buildClientConfiguration(JSONObject worldObject) {
        ConfigurationNode c = configuration;
        JSONObject o = new JSONObject();
        s(o, "type", "FlatMapType");
        s(o, "name", c.getString("name"));
        s(o, "title", c.getString("title"));
        s(o, "icon", c.getString("icon"));
        s(o, "prefix", c.getString("prefix"));
        s(o, "background", c.getString("background"));
        s(o, "nightandday", c.getBoolean("night-and-day",false));
        s(o, "backgroundday", c.getString("backgroundday"));
        s(o, "backgroundnight", c.getString("backgroundnight"));
        a(worldObject, "maps", o);
    }
}
