package org.dynmap.kzedmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import javax.imageio.ImageIO;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.dynmap.Client;
import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.MapManager;
import org.dynmap.TileHashManager;
import org.dynmap.debug.Debug;
import org.dynmap.kzedmap.KzedMap.KzedBufferedImage;
import org.dynmap.utils.FileLockManager;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.json.simple.JSONObject;

public class DefaultTileRenderer implements MapTileRenderer {
    protected static final Color translucent = new Color(0, 0, 0, 0);
    protected String name;
    protected ConfigurationNode configuration;
    protected int maximumHeight = 127;
    protected ColorScheme colorScheme;

    protected HashSet<Integer> highlightBlocks = new HashSet<Integer>();
    protected Color highlightColor = new Color(255, 0, 0);

    protected int   shadowscale[];  /* index=skylight level, value = 256 * scaling value */
    protected int   lightscale[];   /* scale skylight level (light = lightscale[skylight] */
    protected boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */
    protected boolean transparency; /* Is transparency support active? */
    public enum BiomeColorOption {
        NONE, BIOME, TEMPERATURE, RAINFALL
    }
    protected BiomeColorOption biomecolored = BiomeColorOption.NONE; /* Use biome for coloring */
    @Override
    public String getName() {
        return name;
    }

    public boolean isNightAndDayEnabled() { return night_and_day; }

    public DefaultTileRenderer(ConfigurationNode configuration) {
        this.configuration = configuration;
        name = (String) configuration.get("prefix");
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
            int v = Integer.parseInt(String.valueOf(o));
            lightscale = new int[16];
            for(int i = 0; i < 16; i++) {
                if(i < (15-v))
                    lightscale[i] = 0;
                else
                    lightscale[i] = i - (15-v);
            }
        }
        colorScheme = ColorScheme.getScheme((String)configuration.get("colorscheme"));
        night_and_day = configuration.getBoolean("night-and-day", false);
        transparency = configuration.getBoolean("transparency", true);  /* Default on */
        String biomeopt = configuration.getString("biomecolored", "none");
        if(biomeopt.equals("biome")) {
            biomecolored = BiomeColorOption.BIOME;
        }
        else if(biomeopt.equals("temperature")) {
            biomecolored = BiomeColorOption.TEMPERATURE;
        }
        else if(biomeopt.equals("rainfall")) {
            biomecolored = BiomeColorOption.RAINFALL;
        }
        else {
            biomecolored = BiomeColorOption.NONE;
        }
    }
    public boolean isBiomeDataNeeded() { return biomecolored.equals(BiomeColorOption.BIOME); }
    public boolean isRawBiomeDataNeeded() { 
        return biomecolored.equals(BiomeColorOption.RAINFALL) || biomecolored.equals(BiomeColorOption.TEMPERATURE);
    }

    public boolean render(MapChunkCache cache, KzedMapTile tile, File outputFile) {
        World world = tile.getWorld();
        boolean isnether = (world.getEnvironment() == Environment.NETHER);
        KzedBufferedImage im = KzedMap.allocateBufferedImage(KzedMap.tileWidth, KzedMap.tileHeight);
        KzedBufferedImage zim = KzedMap.allocateBufferedImage(KzedMap.tileWidth/2, KzedMap.tileHeight/2);
        boolean isempty = true;
        
        KzedBufferedImage im_day = null;
        KzedBufferedImage zim_day = null;
        if(night_and_day) {
            im_day = KzedMap.allocateBufferedImage(KzedMap.tileWidth, KzedMap.tileHeight);
            zim_day = KzedMap.allocateBufferedImage(KzedMap.tileWidth/2, KzedMap.tileHeight/2);
        }

        int ix = KzedMap.anchorx + tile.px / 2 + tile.py / 2 - ((127-maximumHeight)/2);
        int iy = maximumHeight;
        int iz = KzedMap.anchorz + tile.px / 2 - tile.py / 2 + ((127-maximumHeight)/2);

        /* Don't mess with existing height-clipped renders */
        if(maximumHeight < 127)
            isnether = false;

        int jx, jz;

        int x, y;

        MapIterator mapiter = cache.getIterator(ix, iy, iz);
        
        Color c1 = new Color();
        Color c2 = new Color();
        int[] argb = im.argb_buf;
        int[] zargb = zim.argb_buf;
        Color c1_day = null;
        Color c2_day = null;
        int[] argb_day = null;
        int[] zargb_day = null;
        if(night_and_day) {
            c1_day = new Color();
            c2_day = new Color();
            argb_day = im_day.argb_buf;
            zargb_day = zim_day.argb_buf;
        }
        int rowoff = 0;
        /* draw the map */
        for (y = 0; y < KzedMap.tileHeight;) {
            jx = ix;
            jz = iz;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                mapiter.initialize(jx, iy, jz);   
                scan(world, 0, isnether, c1, c1_day, mapiter);
                mapiter.initialize(jx, iy, jz);   
                scan(world, 2, isnether, c2, c2_day, mapiter);

                argb[rowoff+x] = c1.getARGB();
                argb[rowoff+x-1] = c2.getARGB();
                
                if(night_and_day) {
                    argb_day[rowoff+x] = c1_day.getARGB(); 
                    argb_day[rowoff+x-1] = c2_day.getARGB();
                }
                
                isempty = isempty && c1.isTransparent() && c2.isTransparent();
                
                jx++;
                jz++;

            }
            
            y++;
            rowoff += KzedMap.tileWidth;

            jx = ix;
            jz = iz - 1;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                mapiter.initialize(jx, iy, jz);   
                scan(world, 2, isnether, c1, c1_day, mapiter);
                jx++;
                jz++;
                mapiter.initialize(jx, iy, jz);   
                scan(world, 0, isnether, c2, c2_day, mapiter);

                argb[rowoff+x] = c1.getARGB();
                argb[rowoff+x-1] = c2.getARGB(); 

                if(night_and_day) {
                    argb_day[rowoff+x] = c1_day.getARGB();
                    argb_day[rowoff+x-1] = c2_day.getARGB(); 
                }

                isempty = isempty && c1.isTransparent() && c2.isTransparent();
            }                
            y++;
            rowoff += KzedMap.tileWidth;

            ix++;
            iz--;
        }
        /* Now, compute zoomed tile - bilinear filter 2x2 -> 1x1 */
        doScaleWithBilinear(argb, zargb, KzedMap.tileWidth, KzedMap.tileHeight);
        if(night_and_day) {
            doScaleWithBilinear(argb_day, zargb_day, KzedMap.tileWidth, KzedMap.tileHeight);
        }

        /* Hand encoding and writing file off to MapManager */
        KzedZoomedMapTile zmtile = new KzedZoomedMapTile(tile.getDynmapWorld(),
                (KzedMap) tile.getMap(), tile);
        File zoomFile = MapManager.mapman.getTileFile(zmtile);

        doFileWrites(outputFile, tile, im, im_day, zmtile, zoomFile, zim, zim_day, !isempty);

        return !isempty;
    }

    private void doScaleWithBilinear(int[] argb, int[] zargb, int width, int height) {
        Color c1 = new Color();
        /* Now, compute zoomed tile - bilinear filter 2x2 -> 1x1 */
        for(int y = 0; y < height; y += 2) {
            for(int x = 0; x < width; x += 2) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int alpha = 0;
                for(int yy = y; yy < y+2; yy++) {
                    for(int xx = x; xx < x+2; xx++) {
                        c1.setARGB(argb[(yy*width)+xx]);
                        red += c1.getRed();
                        green += c1.getGreen();
                        blue += c1.getBlue();
                        alpha += c1.getAlpha();
                    }
                }
                c1.setRGBA(red>>2, green>>2, blue>>2, alpha>>2);
                zargb[(y*width/4) + (x/2)] = c1.getARGB();
            }
        }
    }

    private void doFileWrites(final File fname, final KzedMapTile mtile,
        final KzedBufferedImage img, final KzedBufferedImage img_day, 
        final KzedZoomedMapTile zmtile, final File zoomFile,
        final KzedBufferedImage zimg, final KzedBufferedImage zimg_day, boolean rendered) {

        /* Get coordinates of zoomed tile */
        int ox = (mtile.px == zmtile.getTileX())?0:KzedMap.tileWidth/2;
        int oy = (mtile.py == zmtile.getTileY())?0:KzedMap.tileHeight/2;

        /* Test to see if we're unchanged from older tile */
        FileLockManager.getWriteLock(fname);
        TileHashManager hashman = MapManager.mapman.hashman;
        long crc = hashman.calculateTileHash(img.argb_buf);
        boolean updated_fname = false;
        int tx = mtile.px/KzedMap.tileWidth;
        int ty = mtile.py/KzedMap.tileHeight;
        if((!fname.exists()) || (crc != hashman.getImageHashCode(mtile.getKey(), null, tx, ty))) {
            Debug.debug("saving image " + fname.getPath());
            if(!fname.getParentFile().exists())
                fname.getParentFile().mkdirs();
            try {
                FileLockManager.imageIOWrite(img.buf_img, "png", fname);
            } catch (IOException e) {
                Debug.error("Failed to save image: " + fname.getPath(), e);
            } catch (java.lang.NullPointerException e) {
                Debug.error("Failed to save image (NullPointerException): " + fname.getPath(), e);
            }
            MapManager.mapman.pushUpdate(mtile.getWorld(), new Client.Tile(mtile.getFilename()));
            hashman.updateHashCode(mtile.getKey(), null, tx, ty, crc);
            updated_fname = true;
        }
        KzedMap.freeBufferedImage(img);
        FileLockManager.releaseWriteLock(fname);
        MapManager.mapman.updateStatistics(mtile, null, true, updated_fname, !rendered);

        mtile.file = fname;

        boolean updated_dfname = false;
        
        File dfname = new File(mtile.getDynmapWorld().worldtilepath, mtile.getDayFilename());
        if(img_day != null) {
            FileLockManager.getWriteLock(dfname);
            crc = hashman.calculateTileHash(img.argb_buf);
            if((!dfname.exists()) || (crc != hashman.getImageHashCode(mtile.getKey(), "day", tx, ty))) {
                Debug.debug("saving image " + dfname.getPath());
                if(!dfname.getParentFile().exists())
                    dfname.getParentFile().mkdirs();
                try {
                    FileLockManager.imageIOWrite(img_day.buf_img, "png", dfname);
                } catch (IOException e) {
                    Debug.error("Failed to save image: " + dfname.getPath(), e);
                } catch (java.lang.NullPointerException e) {
                    Debug.error("Failed to save image (NullPointerException): " + dfname.getPath(), e);
                }
                MapManager.mapman.pushUpdate(mtile.getWorld(), new Client.Tile(mtile.getDayFilename()));
                hashman.updateHashCode(mtile.getKey(), "day", tx, ty, crc);
                updated_dfname = true;
            }
            KzedMap.freeBufferedImage(img_day);
            FileLockManager.releaseWriteLock(dfname);
            MapManager.mapman.updateStatistics(mtile, "day", true, updated_dfname, !rendered);
        }
        
        // Since we've already got the new tile, and we're on an async thread, just
        // make the zoomed tile here
        boolean ztile_updated = false;
        FileLockManager.getWriteLock(zoomFile);
        if(updated_fname || (!zoomFile.exists())) {
            saveZoomedTile(zmtile, zoomFile, zimg, ox, oy, null);
            MapManager.mapman.pushUpdate(zmtile.getWorld(),
                                         new Client.Tile(zmtile.getFilename()));
            zmtile.getDynmapWorld().enqueueZoomOutUpdate(zoomFile);
            ztile_updated = true;
        }
        KzedMap.freeBufferedImage(zimg);
        FileLockManager.releaseWriteLock(zoomFile);
        MapManager.mapman.updateStatistics(zmtile, null, true, ztile_updated, !rendered);
        
        if(zimg_day != null) {
            File zoomFile_day = new File(zmtile.getDynmapWorld().worldtilepath, zmtile.getDayFilename());
            ztile_updated = false;
            FileLockManager.getWriteLock(zoomFile_day);
            if(updated_dfname || (!zoomFile_day.exists())) {
                saveZoomedTile(zmtile, zoomFile_day, zimg_day, ox, oy, "day");
                MapManager.mapman.pushUpdate(zmtile.getWorld(),
                                             new Client.Tile(zmtile.getDayFilename()));            
                zmtile.getDynmapWorld().enqueueZoomOutUpdate(zoomFile_day);
                ztile_updated = true;
            }
            KzedMap.freeBufferedImage(zimg_day);
            FileLockManager.releaseWriteLock(zoomFile_day);
            MapManager.mapman.updateStatistics(zmtile, "day", true, ztile_updated, !rendered);
        }
    }

    private void saveZoomedTile(final KzedZoomedMapTile zmtile, final File zoomFile,
            final KzedBufferedImage zimg, int ox, int oy, String subkey) {
        BufferedImage zIm = null;
        KzedBufferedImage kzIm = null;
        try {
            zIm = ImageIO.read(zoomFile);
        } catch (IOException e) {
        } catch (IndexOutOfBoundsException e) {
        }

        boolean zIm_allocated = false;
        if (zIm == null) {
            /* create new one */
            kzIm = KzedMap.allocateBufferedImage(KzedMap.tileWidth, KzedMap.tileHeight);
            zIm = kzIm.buf_img;
            zIm_allocated = true;
            Debug.debug("New zoom-out tile created " + zmtile.getFilename());
        } else {
            Debug.debug("Loaded zoom-out tile from " + zmtile.getFilename());
        }

        /* blit scaled rendered tile onto zoom-out tile */
        zIm.setRGB(ox, oy, KzedMap.tileWidth/2, KzedMap.tileHeight/2, zimg.argb_buf, 0, KzedMap.tileWidth/2);

        /* save zoom-out tile */
        if(!zoomFile.getParentFile().exists())
            zoomFile.getParentFile().mkdirs();

        try {
            FileLockManager.imageIOWrite(zIm, "png", zoomFile);
            Debug.debug("Saved zoom-out tile at " + zoomFile.getName());
        } catch (IOException e) {
            Debug.error("Failed to save zoom-out tile: " + zoomFile.getName(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save zoom-out tile (NullPointerException): " + zoomFile.getName(), e);
        }

        if(zIm_allocated)
            KzedMap.freeBufferedImage(kzIm);
        else
            zIm.flush();

    }
    protected void scan(World world, int seq, boolean isnether, final Color result, final Color result_day,
            MapIterator mapiter) {
        int lightlevel = 15;
        int lightlevel_day = 15;
        Biome bio = null;
        double rain = 0.0;
        double temp = 0.0;
        result.setTransparent();
        if(result_day != null)
            result_day.setTransparent();
        for (;;) {
            if (mapiter.getY() < 0) {
                return;
            }
            int id = mapiter.getBlockTypeID();
            int data = 0;
            if(isnether) {    /* Make bedrock ceiling into air in nether */
                if(id != 0) {
                    /* Remember first color we see, in case we wind up solid */
                    if(result.isTransparent())
                        if(colorScheme.colors[id] != null)
                            result.setColor(colorScheme.colors[id][seq]);
                    id = 0;
                }
                else
                    isnether = false;
            }
            if(id != 0) {       /* No update needed for air */
                switch(biomecolored) {
                    case NONE:
                        if(colorScheme.datacolors[id] != null) {    /* If data colored */
                            data = mapiter.getBlockData();
                        }
                        break;
                    case BIOME:
                        bio = mapiter.getBiome();
                        break;
                    case RAINFALL:
                        rain = mapiter.getRawBiomeRainfall();
                        break;
                    case TEMPERATURE:
                        temp = mapiter.getRawBiomeTemperature();
                        break;
                }
                if((shadowscale != null) && (mapiter.getY() < 127)) {
                    /* Find light level of previous chunk */
                    switch(seq) {
                        case 0:
                        case 2:
                            mapiter.incrementY();
                            break;
                        case 1:
                            mapiter.incrementX();
                            break;
                        case 3:
                            mapiter.decrementZ();
                            break;
                    }
                    lightlevel = lightlevel_day = mapiter.getBlockSkyLight();
                    if(lightscale != null)
                        lightlevel = lightscale[lightlevel];
                    if((lightlevel < 15) || (lightlevel_day < 15)) {
                        int emitted = mapiter.getBlockEmittedLight();
                        lightlevel = Math.max(emitted, lightlevel);                                
                        lightlevel_day = Math.max(emitted, lightlevel_day);                                
                    }
                    switch(seq) {
                        case 0:
                        case 2:
                            mapiter.decrementY();
                            break;
                        case 1:
                            mapiter.decrementX();
                            break;
                        case 3:
                            mapiter.incrementZ();
                            break;
                    }
                }
            }
            
            switch (seq) {
            case 0:
                mapiter.decrementX();
                break;
            case 1:
            case 3:
                mapiter.decrementY();
                break;
            case 2:
                mapiter.incrementZ();
                break;
            }

            seq = (seq + 1) & 3;

            if (id != 0) {
                if (highlightBlocks.contains(id)) {
                    result.setColor(highlightColor);
                    return;
                }
                Color[] colors = null;
                switch(biomecolored) {
                    case NONE:
                        if(data != 0)
                            colors = colorScheme.datacolors[id][data];
                        else
                            colors = colorScheme.colors[id];
                        break;
                    case BIOME:
                        if(bio != null)
                            colors = colorScheme.biomecolors[bio.ordinal()];
                        break;
                    case RAINFALL:
                        colors = colorScheme.getRainColor(rain);
                        break;
                    case TEMPERATURE:
                        colors = colorScheme.getTempColor(temp);
                        break;
                }
                if (colors != null) {
                    Color c = colors[seq];
                    if (c.getAlpha() > 0) {
                        /* we found something that isn't transparent, or not doing transparency */
                        if ((!transparency) || (c.getAlpha() == 255)) {
                            /* it's opaque - the ray ends here */
                            result.setARGB(c.getARGB() | 0xFF000000);
                            if(lightlevel < 15) {  /* Not full light? */
                                shadowColor(result, lightlevel);
                            }
                            if(result_day != null) {
                                if(lightlevel_day == lightlevel)    /* Same light = same result */
                                    result_day.setColor(result);
                                else {
                                    result_day.setColor(c);
                                    if(lightlevel_day < 15)
                                        shadowColor(result_day, lightlevel_day);
                                }
                            }
                            return;
                        }

                        /* this block is transparent, so recurse */
                        scan(world, seq, isnether, result, result_day, mapiter);

                        int cr = c.getRed();
                        int cg = c.getGreen();
                        int cb = c.getBlue();
                        int ca = c.getAlpha();
                        if(lightlevel < 15) {
                            int scale = shadowscale[lightlevel];
                            cr = (cr * scale) >> 8;
                            cg = (cg * scale) >> 8;
                            cb = (cb * scale) >> 8;
                        }
                        cr *= ca;
                        cg *= ca;
                        cb *= ca;
                        int na = 255 - ca;
                        result.setRGBA((result.getRed() * na + cr) >> 8, (result.getGreen() * na + cg) >> 8, (result.getBlue() * na + cb) >> 8, 255);
                        /* Handle day also */
                        if(result_day != null) {
                            cr = c.getRed();
                            cg = c.getGreen();
                            cb = c.getBlue();
                            if(lightlevel_day < 15) {
                                int scale = shadowscale[lightlevel_day];
                                cr = (cr * scale) >> 8;
                                cg = (cg * scale) >> 8;
                                cb = (cb * scale) >> 8;
                            }
                            cr *= ca;
                            cg *= ca;
                            cb *= ca;
                            result_day.setRGBA((result_day.getRed() * na + cr) >> 8, (result_day.getGreen() * na + cg) >> 8, (result_day.getBlue() * na + cb) >> 8, 
                                               255);
                        }
                        return;
                    }
                }
            }
        }
    }
    private final void shadowColor(Color c, int lightlevel) {
        int scale = shadowscale[lightlevel];
        if(scale < 256)
            c.setRGBA((c.getRed() * scale) >> 8, (c.getGreen() * scale) >> 8, 
                (c.getBlue() * scale) >> 8, c.getAlpha());
    }

    @Override
    public void buildClientConfiguration(JSONObject worldObject) {
        ConfigurationNode c = configuration;
        JSONObject o = new JSONObject();
        s(o, "type", "KzedMapType");
        s(o, "name", c.getString("name"));
        s(o, "title", c.getString("title"));
        s(o, "icon", c.getString("icon"));
        s(o, "prefix", c.getString("prefix"));
        s(o, "background", c.getString("background"));
        s(o, "nightandday", c.getBoolean("night-and-day", false));
        s(o, "backgroundday", c.getString("backgroundday"));
        s(o, "backgroundnight", c.getString("backgroundnight"));
        a(worldObject, "maps", o);
    }
}
