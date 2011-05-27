package org.dynmap.kzedmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import javax.imageio.ImageIO;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.dynmap.Client;
import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.debug.Debug;
import org.dynmap.MapChunkCache;
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
    @Override
    public String getName() {
        return name;
    }

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
    }

    public boolean render(MapChunkCache cache, KzedMapTile tile, File outputFile) {
        World world = tile.getWorld();
        boolean isnether = (world.getEnvironment() == Environment.NETHER);
        BufferedImage im = KzedMap.allocateBufferedImage(KzedMap.tileWidth, KzedMap.tileHeight);
        BufferedImage zim = KzedMap.allocateBufferedImage(KzedMap.tileWidth/2, KzedMap.tileHeight/2);
        boolean isempty = true;
        
        BufferedImage im_day = null;
        BufferedImage zim_day = null;
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

        MapChunkCache.MapIterator mapiter = cache.getIterator(ix, iy, iz);
        
        Color c1 = new Color();
        Color c2 = new Color();
        int[] argb = new int[KzedMap.tileWidth];
        int[] zargb = new int[4*KzedMap.tileWidth/2];
        Color c1_day = null;
        Color c2_day = null;
        int[] argb_day = null;
        int[] zargb_day = null;
        if(night_and_day) {
            c1_day = new Color();
            c2_day = new Color();
            argb_day = new int[KzedMap.tileWidth];
            zargb_day = new int[4*KzedMap.tileWidth/2];
        }
        /* draw the map */
        for (y = 0; y < KzedMap.tileHeight;) {
            jx = ix;
            jz = iz;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                mapiter.initialize(jx, iy, jz);   
                scan(world, 0, isnether, c1, c1_day, mapiter);
                mapiter.initialize(jx, iy, jz);   
                scan(world, 2, isnether, c2, c2_day, mapiter);

                argb[x] = c1.getARGB();
                argb[x-1] = c2.getARGB();
                
                if(night_and_day) {
                    argb_day[x] = c1_day.getARGB(); 
                    argb_day[x-1] = c2_day.getARGB();
                }
                
                isempty = isempty && c1.isTransparent() && c2.isTransparent();
                
                jx++;
                jz++;

            }
            im.setRGB(0, y, KzedMap.tileWidth, 1, argb, 0, KzedMap.tileWidth);
            if(night_and_day)
                im_day.setRGB(0, y, KzedMap.tileWidth, 1, argb_day, 0, KzedMap.tileWidth);
            /* Sum up zoomed pixels - bilinar filter */
            for(x = 0; x < KzedMap.tileWidth / 2; x++) {
                c1.setARGB(argb[2*x]);
                c2.setARGB(argb[2*x+1]);
                for(int i = 0; i < 4; i++)
                    zargb[4*x+i] = c1.getComponent(i) + c2.getComponent(i);
            }
            if(night_and_day) {
                for(x = 0; x < KzedMap.tileWidth / 2; x++) {
                    c1.setARGB(argb_day[2*x]);
                    c2.setARGB(argb_day[2*x+1]);
                    for(int i = 0; i < 4; i++)
                        zargb_day[4*x+i] = c1.getComponent(i) + c2.getComponent(i);
                }   
            }
            
            y++;

            jx = ix;
            jz = iz - 1;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                mapiter.initialize(jx, iy, jz);   
                scan(world, 2, isnether, c1, c1_day, mapiter);
                jx++;
                jz++;
                mapiter.initialize(jx, iy, jz);   
                scan(world, 0, isnether, c2, c2_day, mapiter);

                argb[x] = c1.getARGB();
                argb[x-1] = c2.getARGB(); 

                if(night_and_day) {
                    argb_day[x] = c1_day.getARGB();
                    argb_day[x-1] = c2_day.getARGB(); 
                }

                isempty = isempty && c1.isTransparent() && c2.isTransparent();
            }
            im.setRGB(0, y, KzedMap.tileWidth, 1, argb, 0, KzedMap.tileWidth);
            if(night_and_day)
                im_day.setRGB(0, y, KzedMap.tileWidth, 1, argb_day, 0, KzedMap.tileWidth);
                
            /* Finish summing values for zoomed pixels */
            /* Sum up zoomed pixels - bilinar filter */
            for(x = 0; x < KzedMap.tileWidth / 2; x++) {
                c1.setARGB(argb[2*x]);
                c2.setARGB(argb[2*x+1]);
                for(int i = 0; i < 4; i++)
                    zargb[4*x+i] = (zargb[4*x+i] + c1.getComponent(i) + c2.getComponent(i)) >> 2;
                c1.setRGBA(zargb[4*x+1], zargb[4*x+2], zargb[4*x+3], zargb[4*x]);
                zargb[x] = c1.getARGB();
            }
            if(night_and_day) {
                for(x = 0; x < KzedMap.tileWidth / 2; x++) {
                    c1.setARGB(argb_day[2*x]);
                    c2.setARGB(argb_day[2*x+1]);
                    for(int i = 0; i < 4; i++)
                        zargb_day[4*x+i] = (zargb_day[4*x+i] + c1.getComponent(i) + c2.getComponent(i)) >> 2;
                    c1.setRGBA(zargb_day[4*x+1], zargb_day[4*x+2], zargb_day[4*x+3], zargb_day[4*x]);
                    zargb_day[x] = c1.getARGB();
                }   
            }
            zim.setRGB(0, y/2, KzedMap.tileWidth/2, 1, zargb, 0, KzedMap.tileWidth/2);
            if(night_and_day)
                zim_day.setRGB(0, y/2, KzedMap.tileWidth/2, 1, zargb_day, 0, KzedMap.tileWidth/2);
                
            y++;

            ix++;
            iz--;
        }

        /* Hand encoding and writing file off to MapManager */
        final File fname = outputFile;
        final KzedMapTile mtile = tile;
        final BufferedImage img = im;
        final BufferedImage zimg = zim;
        final BufferedImage img_day = im_day;
        final BufferedImage zimg_day = zim_day;
        final KzedZoomedMapTile zmtile = new KzedZoomedMapTile(mtile.getWorld(),
                (KzedMap) mtile.getMap(), mtile);
        final File zoomFile = MapManager.mapman.getTileFile(zmtile);

        MapManager.mapman.enqueueImageWrite(new Runnable() {
            public void run() {
                doFileWrites(fname, mtile, img, img_day, zmtile, zoomFile, zimg, zimg_day);
            }
        });

        return !isempty;
    }

    private void doFileWrites(final File fname, final KzedMapTile mtile,
        final BufferedImage img, final BufferedImage img_day, 
        final KzedZoomedMapTile zmtile, final File zoomFile,
        final BufferedImage zimg, final BufferedImage zimg_day) {
        Debug.debug("saving image " + fname.getPath());
        try {
            ImageIO.write(img, "png", fname);
        } catch (IOException e) {
            Debug.error("Failed to save image: " + fname.getPath(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save image (NullPointerException): " + fname.getPath(), e);
        }
        KzedMap.freeBufferedImage(img);
        if(img_day != null) {
            File dfname = new File(fname.getParent(), mtile.getDayFilename());
            Debug.debug("saving image " + dfname.getPath());
            try {
                ImageIO.write(img_day, "png", dfname);
            } catch (IOException e) {
                Debug.error("Failed to save image: " + dfname.getPath(), e);
            } catch (java.lang.NullPointerException e) {
                Debug.error("Failed to save image (NullPointerException): " + dfname.getPath(), e);
            }
            KzedMap.freeBufferedImage(img_day);
        }
        mtile.file = fname;
        // Since we've already got the new tile, and we're on an async thread, just
        // make the zoomed tile here
        int px = mtile.px;
        int py = mtile.py;
        int zpx = zmtile.getTileX();
        int zpy = zmtile.getTileY();

        /* scaled size */
        int scw = KzedMap.tileWidth / 2;
        int sch = KzedMap.tileHeight / 2;

        /* origin in zoomed-out tile */
        int ox = 0;
        int oy = 0;

        if (zpx != px)
            ox = scw;
        if (zpy != py)
            oy = sch;

        BufferedImage zIm = null;
        try {
            zIm = ImageIO.read(zoomFile);
        } catch (IOException e) {
        } catch (IndexOutOfBoundsException e) {
        }

        boolean zIm_allocated = false;
        if (zIm == null) {
            /* create new one */
            zIm = KzedMap.allocateBufferedImage(KzedMap.tileWidth, KzedMap.tileHeight);
            zIm_allocated = true;
            Debug.debug("New zoom-out tile created " + zmtile.getFilename());
        } else {
            Debug.debug("Loaded zoom-out tile from " + zmtile.getFilename());
        }

        /* blit scaled rendered tile onto zoom-out tile */
        int[] pix = zimg.getRGB(0, 0, KzedMap.tileWidth/2, KzedMap.tileHeight/2, null, 0, KzedMap.tileWidth/2);
        zIm.setRGB(ox, oy, KzedMap.tileWidth/2, KzedMap.tileHeight/2, pix, 0, KzedMap.tileWidth/2);
        KzedMap.freeBufferedImage(zimg);

        /* save zoom-out tile */

        try {
            ImageIO.write(zIm, "png", zoomFile);
            Debug.debug("Saved zoom-out tile at " + zoomFile.getName());
        } catch (IOException e) {
            Debug.error("Failed to save zoom-out tile: " + zoomFile.getName(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save zoom-out tile (NullPointerException): " + zoomFile.getName(), e);
        }
        if(zIm_allocated)
            KzedMap.freeBufferedImage(zIm);
        else
            zIm.flush();
        
        if(zimg_day != null) {
            File zoomFile_day = new File(zoomFile.getParent(), zmtile.getDayFilename());
            
            zIm = null;
            try {
                zIm = ImageIO.read(zoomFile_day);
            } catch (IOException e) {
            } catch (IndexOutOfBoundsException e) {
            }

            zIm_allocated = false;
            if (zIm == null) {
                /* create new one */
                zIm = KzedMap.allocateBufferedImage(KzedMap.tileWidth, KzedMap.tileHeight);
                zIm_allocated = true;
                Debug.debug("New zoom-out tile created " + zmtile.getFilename());
            } else {
                Debug.debug("Loaded zoom-out tile from " + zmtile.getFilename());
            }

            /* blit scaled rendered tile onto zoom-out tile */
            pix = zimg_day.getRGB(0, 0, KzedMap.tileWidth/2, KzedMap.tileHeight/2, null, 0, KzedMap.tileWidth/2);
            zIm.setRGB(ox, oy, KzedMap.tileWidth/2, KzedMap.tileHeight/2, pix, 0, KzedMap.tileWidth/2);
            KzedMap.freeBufferedImage(zimg_day);

            /* save zoom-out tile */

            try {
                ImageIO.write(zIm, "png", zoomFile_day);
                Debug.debug("Saved zoom-out tile at " + zoomFile_day.getName());
            } catch (IOException e) {
                Debug.error("Failed to save zoom-out tile: " + zoomFile_day.getName(), e);
            } catch (java.lang.NullPointerException e) {
                Debug.error("Failed to save zoom-out tile (NullPointerException): " + zoomFile_day.getName(), e);
            }
            if(zIm_allocated)
                KzedMap.freeBufferedImage(zIm);
            else
                zIm.flush();
        }
        /* Push updates for both files.*/
        MapManager.mapman.pushUpdate(mtile.getWorld(),
            new Client.Tile(mtile.getFilename()));
        MapManager.mapman.pushUpdate(zmtile.getWorld(),
                new Client.Tile(zmtile.getFilename()));
        if(img_day != null) {
            MapManager.mapman.pushUpdate(mtile.getWorld(),
                                         new Client.Tile(mtile.getDayFilename()));
            MapManager.mapman.pushUpdate(zmtile.getWorld(),
                                         new Client.Tile(zmtile.getDayFilename()));            
        }
    }

    protected void scan(World world, int seq, boolean isnether, final Color result, final Color result_day,
            MapChunkCache.MapIterator mapiter) {
        int lightlevel = 15;
        int lightlevel_day = 15;
        result.setTransparent();
        for (;;) {
            if (mapiter.y < 0) {
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
                if(colorScheme.datacolors[id] != null) {    /* If data colored */
                    data = mapiter.getBlockData();
                }
                if((shadowscale != null) && (mapiter.y < 127)) {
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
                Color[] colors;
                if(data != 0)
                    colors = colorScheme.datacolors[id][data];
                else
                    colors = colorScheme.colors[id];
                if (colors != null) {
                    Color c = colors[seq];
                    if (c.getAlpha() > 0) {
                        /* we found something that isn't transparent! */
                        if (c.getAlpha() == 255) {
                            /* it's opaque - the ray ends here */
                            result.setColor(c);
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
        s(o, "nightandday", c.getBoolean("night-and-day", false));
        a(worldObject, "maps", o);
    }
}
