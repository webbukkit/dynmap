package org.dynmap.kzedmap;

import org.dynmap.DynmapWorld;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.utils.MapChunkCache;
import org.json.simple.JSONObject;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.awt.image.ColorModel;
import java.awt.image.Raster;

public class KzedMap extends MapType {
    protected static final Logger log = Logger.getLogger("Minecraft");
    protected static final String LOG_PREFIX = "[dynmap] ";

    /* dimensions of a map tile */
    public static final int tileWidth = 128;
    public static final int tileHeight = 128;

    /*
     * (logical!) dimensions of a zoomed out map tile must be twice the size of
     * the normal tile
     */
    public static final int zTileWidth = 256;
    public static final int zTileHeight = 256;

    /* map x, y, z for projection origin */
    public static final int anchorx = 0;
    public static final int anchory = 127;
    public static final int anchorz = 0;
    
    MapTileRenderer[] renderers;

    /* BufferedImage with direct access to its ARGB-formatted data buffer */
    public static class KzedBufferedImage {
        public BufferedImage buf_img;
        public int[] argb_buf;
        public int width;
        public int height;
    }

    /* BufferedImage cache - we use the same things a lot... */
    private static Object lock = new Object();
    private static HashMap<Long, LinkedList<KzedBufferedImage>> imgcache = 
        new HashMap<Long, LinkedList<KzedBufferedImage>>(); /* Indexed by resolution - X<<32+Y */
    private static final int CACHE_LIMIT = 10;

    public KzedMap(ConfigurationNode configuration) {
        Log.verboseinfo("Loading renderers for map '" + getClass().toString() + "'...");
        List<MapTileRenderer> renderers = configuration.<MapTileRenderer>createInstances("renderers", new Class<?>[0], new Object[0]);
        this.renderers = new MapTileRenderer[renderers.size()];
        renderers.toArray(this.renderers);
        Log.verboseinfo("Loaded " + renderers.size() + " renderers for map '" + getClass().toString() + "'.");
    }

    @Override
    public MapTile[] getTiles(Location l) {
        DynmapWorld world = MapManager.mapman.getWorld(l.getWorld().getName());

        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();

        int dx = x - anchorx;
        int dy = y - anchory;
        int dz = z - anchorz;
        int px = dx + dz;
        int py = dx - dz - dy;

        int tx = tilex(px);
        int ty = tiley(py);

        ArrayList<MapTile> tiles = new ArrayList<MapTile>();

        addTile(tiles, world, tx, ty);

        boolean ledge = tilex(px - 4) != tx;
        boolean tedge = tiley(py - 4) != ty;
        boolean redge = tilex(px + 4) != tx;
        boolean bedge = tiley(py + 4) != ty;

        if (ledge)
            addTile(tiles, world, tx - tileWidth, ty);
        if (redge)
            addTile(tiles, world, tx + tileWidth, ty);
        if (tedge)
            addTile(tiles, world, tx, ty - tileHeight);
        if (bedge)
            addTile(tiles, world, tx, ty + tileHeight);

        if (ledge && tedge)
            addTile(tiles, world, tx - tileWidth, ty - tileHeight);
        if (ledge && bedge)
            addTile(tiles, world, tx - tileWidth, ty + tileHeight);
        if (redge && tedge)
            addTile(tiles, world, tx + tileWidth, ty - tileHeight);
        if (redge && bedge)
            addTile(tiles, world, tx + tileWidth, ty + tileHeight);

        MapTile[] result = new MapTile[tiles.size()];
        tiles.toArray(result);
        return result;
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;
            DynmapWorld world = tile.getDynmapWorld();
            MapTileRenderer renderer = t.renderer;
            return new MapTile[] {
                new KzedMapTile(world, this, renderer, t.px - tileWidth, t.py),
                new KzedMapTile(world, this, renderer, t.px + tileWidth, t.py),
                new KzedMapTile(world, this, renderer, t.px, t.py - tileHeight),
                new KzedMapTile(world, this, renderer, t.px, t.py + tileHeight) };
        }
        return new MapTile[0];
    }

    public void addTile(ArrayList<MapTile> tiles, DynmapWorld world, int px, int py) {
        for (int i = 0; i < renderers.length; i++) {
            tiles.add(new KzedMapTile(world, this, renderers[i], px, py));
        }
    }

    public void invalidateTile(MapTile tile) {
        onTileInvalidated.trigger(tile);
    }

    /**
     * Test if point x,z is inside rectangle with corner at r0x,r0z and with
     * size vectors s1x,s1z and s2x,s2z
     *
     */
    private boolean testPointInRectangle(int x, int z, int r0x, int r0z, int s1x, int s1z,
            int s2x, int s2z) {
        int xr = x - r0x;
        int zr = z - r0z;   /* Get position relative to rectangle corner */
        int dots1 = xr*s1x + zr*s1z;
        int dots2 = xr*s2x + zr*s2z;
        /* If dot product of relative point and each side is between zero and dot product
         * of each side and itself, we're inside
         */
        if((dots1 >= 0) && (dots1 <= (s1x*s1x+s1z*s1z)) &&
                (dots2 >= 0) && (dots2 <= (s2x*s2x+s2z*s2z))) {
            return true;
        }
        return false;
    }
    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;

            int ix = KzedMap.anchorx + t.px / 2 + t.py / 2;
            //int iy = 127;
            int iz = KzedMap.anchorz + t.px / 2 - t.py / 2;

            int x1 = ix - KzedMap.tileHeight / 2;
            int x2 = ix + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

            int z1 = iz - KzedMap.tileHeight / 2;
            int z2 = iz + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

            int x, z;

            /* Actual pattern of chunks needed is create by the slanted
             * square prism corresponding to the render path of the tile.
             * Top of prism (corresponding to y=127) is diamond shape from
             * ix, iz to ix+64,iz+64 to ix+128,iz to ix+64,iz-64
             * Bottom is same shape, offset by -64 on x, +64 on z (net
             * render path to y=0), correspond to ix-64, iz+64 to
             * ix,iz+128 to ix+64,iz+64 to ix,iz.  Projection of
             * the prism on to the x,z plane (which is all that matters for
             * chunks) yields a diagonal rectangular area from ix-64(x1),iz+64
             * to ix,iz+128(z2) to ix+128(x2),iz to ix+64,iz-64(z1).
             * Chunks outside this are not needed - we scan a simple rectangle
             * (chunk grid aligned) and skip adding the ones that are outside.
             * This results in 42% less chunks being loaded.
             */
            ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>();

            for (x = x1; x < x2; x += 16) {
                for (z = z1; z < z2; z += 16) {
                    /* If any of the chunk corners are inside the rectangle, we need it */
                    if((!testPointInRectangle(x, z, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)) &&
                        (!testPointInRectangle(x+15, z, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)) &&
                        (!testPointInRectangle(x+15, z+15, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)) &&
                        (!testPointInRectangle(x, z+15, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)))
                        continue;
                    DynmapChunk chunk = new DynmapChunk(x / 16, z / 16);
                    chunks.add(chunk);
                }
            }
            return chunks;
        } else {
            return new ArrayList<DynmapChunk>();
        }
    }

    @Override
    public boolean render(MapChunkCache cache, MapTile tile, File outputFile) {
        if (tile instanceof KzedMapTile) {
            return ((KzedMapTile) tile).renderer.render(cache, (KzedMapTile) tile, outputFile);
        }
        return false;
    }

    /* tile X for position x */
    static int tilex(int x) {
        if (x < 0)
            return x - (tileWidth + (x % tileWidth));
        else
            return x - (x % tileWidth);
    }

    /* tile Y for position y */
    static int tiley(int y) {
        if (y < 0)
            return y - (tileHeight + (y % tileHeight));
        else
            return y - (y % tileHeight);
    }

    /* zoomed-out tile X for tile position x */
    static int ztilex(int x) {
        if (x < 0)
            return x + x % zTileWidth;
        else
            return x - (x % zTileWidth);
    }

    /* zoomed-out tile Y for tile position y */
    static int ztiley(int y) {
        if (y < 0)
            return y + y % zTileHeight;
        // return y - (zTileHeight + (y % zTileHeight));
        else
            return y - (y % zTileHeight);
    }

    /**
     * Allocate buffered image from pool, if possible
     * @param x - x dimension
     * @param y - y dimension
     */
    public static KzedBufferedImage allocateBufferedImage(int x, int y) {
        KzedBufferedImage img = null;
        synchronized(lock) {
            long k = (x<<16) + y;
            LinkedList<KzedBufferedImage> ll = imgcache.get(k);
            if(ll != null) {
                img = ll.poll();
            }
        }
        if(img != null) {   /* Got it - reset it for use */
            Arrays.fill(img.argb_buf, 0);
        }
        else {
            img = new KzedBufferedImage();
            img.width = x;
            img.height = y;
            img.argb_buf = new int[x*y];
        }
        img.buf_img = createBufferedImage(img.argb_buf, img.width, img.height);
        return img;
    }
    
    /**
     * Return buffered image to pool
     */
    public static void freeBufferedImage(KzedBufferedImage img) {
        img.buf_img.flush();
        img.buf_img = null; /* Toss bufferedimage - seems to hold on to other memory */
        synchronized(lock) {
            long k = (img.width<<16) + img.height;
            LinkedList<KzedBufferedImage> ll = imgcache.get(k);
            if(ll == null) {
                ll = new LinkedList<KzedBufferedImage>();
                imgcache.put(k, ll);
            }
            if(ll.size() < CACHE_LIMIT) {
                ll.add(img);
                img = null;
            }
        }
    }    

    public boolean isBiomeDataNeeded() {
        for(MapTileRenderer r : renderers) {
           if(r.isBiomeDataNeeded())
               return true;
        }
        return false;
    }

    public boolean isRawBiomeDataNeeded() { 
        for(MapTileRenderer r : renderers) {
            if(r.isRawBiomeDataNeeded())
                return true;
         }
         return false;
     }

    public List<String> baseZoomFilePrefixes() {
        ArrayList<String> s = new ArrayList<String>();
        for(MapTileRenderer r : renderers) {
            s.add("z" + r.getName());
            if(r.isNightAndDayEnabled())
                s.add("z" + r.getName() + "_day");
        }
        return s;
    }
    /* Return negative to flag negative X walk */
    public int baseZoomFileStepSize() { return -zTileWidth; }

    private static final int[] stepseq = { 0, 2, 1, 3 };
    
    public int[] zoomFileStepSequence() { return stepseq; }
    /* How many bits of coordinate are shifted off to make big world directory name */
    public int getBigWorldShift() { return 12; }

    public String getName() {
        return "KzedMap";
    }

    @Override
    public void buildClientConfiguration(JSONObject worldObject) {
        for(MapTileRenderer renderer : renderers) {
            renderer.buildClientConfiguration(worldObject);
        }
    }

    /* ARGB band masks */
    private static final int [] band_masks = {0xFF0000, 0xFF00, 0xff, 0xff000000};

    /**
     * Build BufferedImage from provided ARGB array and dimensions
     */
    public static BufferedImage createBufferedImage(int[] argb_buf, int w, int h) {
        /* Create integer-base data buffer */
        DataBuffer db = new DataBufferInt (argb_buf, w*h);
        /* Create writable raster */
        WritableRaster raster = Raster.createPackedRaster(db, w, h, w, band_masks, null);
        /* RGB color model */
        ColorModel color_model = ColorModel.getRGBdefault ();
        /* Return buffered image */
        return new BufferedImage (color_model, raster, false, null);
    }
}
