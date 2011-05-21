package org.dynmap.kzedmap;

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
import org.dynmap.MapManager;
import org.dynmap.debug.Debug;
import org.dynmap.MapChunkCache;

public class DefaultTileRenderer implements MapTileRenderer {
    protected static final Color translucent = new Color(0, 0, 0, 0);
    protected String name;
    protected int maximumHeight = 127;
    protected ColorScheme colorScheme;

    protected HashSet<Integer> highlightBlocks = new HashSet<Integer>();
    protected Color highlightColor = new Color(255, 0, 0);

    protected int   shadowscale[];  /* index=skylight level, value = 256 * scaling value */
    @Override
    public String getName() {
        return name;
    }

    public DefaultTileRenderer(ConfigurationNode configuration) {
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
                for(int i = 0; i < 16; i++) {
                    double v = 256.0 * (1.0 - (shadowweight * (15-i) / 15.0));
                    shadowscale[i] = (int)v;
                    if(shadowscale[i] > 256) shadowscale[i] = 256;
                    if(shadowscale[i] < 0) shadowscale[i] = 0;
                }
            }
        }
        colorScheme = ColorScheme.getScheme((String)configuration.get("colorscheme"));
    }

    public boolean render(MapChunkCache cache, KzedMapTile tile, File outputFile) {
        World world = tile.getWorld();
        boolean isnether = (world.getEnvironment() == Environment.NETHER);
        BufferedImage im = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage zim = new BufferedImage(KzedMap.tileWidth/2, KzedMap.tileHeight/2, BufferedImage.TYPE_INT_RGB);
        WritableRaster r = im.getRaster();
        WritableRaster zr = zim.getRaster();
        boolean isempty = true;

        int ix = KzedMap.anchorx + tile.px / 2 + tile.py / 2 - ((127-maximumHeight)/2);
        int iy = maximumHeight;
        int iz = KzedMap.anchorz + tile.px / 2 - tile.py / 2 + ((127-maximumHeight)/2);

        /* Don't mess with existing height-clipped renders */
        if(maximumHeight < 127)
            isnether = false;

        int jx, jz;

        int x, y;

        Color c1 = new Color();
        Color c2 = new Color();
        int[] rgb = new int[3*KzedMap.tileWidth];
        int[] zrgb = new int[3*KzedMap.tileWidth/2];
        /* draw the map */
        for (y = 0; y < KzedMap.tileHeight;) {
            jx = ix;
            jz = iz;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                scan(world, jx, iy, jz, 0, isnether, c1, cache);
                scan(world, jx, iy, jz, 2, isnether, c2, cache);

                rgb[3*x] = c1.getRed(); 
                rgb[3*x+1] = c1.getGreen(); 
                rgb[3*x+2] = c1.getBlue();
                rgb[3*x-3] = c2.getRed(); 
                rgb[3*x-2] = c2.getGreen(); 
                rgb[3*x-1] = c2.getBlue();

                isempty = isempty && c1.isTransparent() && c2.isTransparent();
                
                jx++;
                jz++;

            }
            r.setPixels(0, y, KzedMap.tileWidth, 1, rgb);
            /* Sum up zoomed pixels - bilinar filter */
            for(x = 0; x < KzedMap.tileWidth / 2; x++) {
                zrgb[3*x] = rgb[6*x] + rgb[6*x+3];
                zrgb[3*x+1] = rgb[6*x+1] + rgb[6*x+4];
                zrgb[3*x+2] = rgb[6*x+2] + rgb[6*x+5];                
            }

            y++;

            jx = ix;
            jz = iz - 1;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                scan(world, jx, iy, jz, 2, isnether, c1, cache);
                jx++;
                jz++;
                scan(world, jx, iy, jz, 0, isnether, c2, cache);

                rgb[3*x] = c1.getRed(); 
                rgb[3*x+1] = c1.getGreen(); 
                rgb[3*x+2] = c1.getBlue();
                rgb[3*x-3] = c2.getRed(); 
                rgb[3*x-2] = c2.getGreen(); 
                rgb[3*x-1] = c2.getBlue();

                isempty = isempty && c1.isTransparent() && c2.isTransparent();
            }
            r.setPixels(0, y, KzedMap.tileWidth, 1, rgb);
            /* Finish summing values for zoomed pixels */
            for(x = 0; x < KzedMap.tileWidth / 2; x++) {
                zrgb[3*x] = (zrgb[3*x] + rgb[6*x] + rgb[6*x+3]) >> 2;
                zrgb[3*x+1] = (zrgb[3*x+1] + rgb[6*x+1] + rgb[6*x+4]) >> 2;
                zrgb[3*x+2] = (zrgb[3*x+2] + rgb[6*x+2] + rgb[6*x+5]) >> 2;          
            }
            zr.setPixels(0, y/2, KzedMap.tileWidth/2, 1, zrgb);
            
            y++;

            ix++;
            iz--;
        }

        /* Hand encoding and writing file off to MapManager */
        final File fname = outputFile;
        final KzedMapTile mtile = tile;
        final BufferedImage img = im;
        final BufferedImage zimg = zim;
        final KzedZoomedMapTile zmtile = new KzedZoomedMapTile(mtile.getWorld(),
                (KzedMap) mtile.getMap(), mtile);
        final File zoomFile = MapManager.mapman.getTileFile(zmtile);

        MapManager.mapman.enqueueImageWrite(new Runnable() {
            public void run() {
                doFileWrites(fname, mtile, img, zmtile, zoomFile, zimg);
            }
        });

        return !isempty;
    }

    private void doFileWrites(final File fname, final KzedMapTile mtile,
        final BufferedImage img, final KzedZoomedMapTile zmtile, final File zoomFile,
        final BufferedImage zimg) {
        Debug.debug("saving image " + fname.getPath());
        try {
            ImageIO.write(img, "png", fname);
        } catch (IOException e) {
            Debug.error("Failed to save image: " + fname.getPath(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save image (NullPointerException): " + fname.getPath(), e);
        }
        img.flush();
        
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

        if (zIm == null) {
            /* create new one */
            zIm = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);
            Debug.debug("New zoom-out tile created " + zmtile.getFilename());
        } else {
            Debug.debug("Loaded zoom-out tile from " + zmtile.getFilename());
        }

        /* blit scaled rendered tile onto zoom-out tile */
        WritableRaster zim = zIm.getRaster();
        zim.setRect(ox, oy, zimg.getRaster());        
        zimg.flush();

        /* save zoom-out tile */

        try {
            ImageIO.write(zIm, "png", zoomFile);
            Debug.debug("Saved zoom-out tile at " + zoomFile.getName());
        } catch (IOException e) {
            Debug.error("Failed to save zoom-out tile: " + zoomFile.getName(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save zoom-out tile (NullPointerException): " + zoomFile.getName(), e);
        }
        zIm.flush();
        /* Push updates for both files.*/
        MapManager.mapman.pushUpdate(mtile.getWorld(),
            new Client.Tile(mtile.getFilename()));
        MapManager.mapman.pushUpdate(zmtile.getWorld(),
                new Client.Tile(zmtile.getFilename()));
    }

    protected void scan(World world, int x, int y, int z, int seq, boolean isnether, final Color result,
            MapChunkCache cache) {
        int lightlevel = 15;
        result.setTransparent();
        for (;;) {
            if (y < 0) {
                return;
            }
            int id = cache.getBlockTypeID(x, y, z);
            byte data = 0;
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
                    data = cache.getBlockData(x, y, z);
                }
                if((shadowscale != null) && (y < 127)) {
                    /* Find light level of previous chunk */
                    switch(seq) {
                        case 0:
                            lightlevel = cache.getBlockSkyLight(x, y+1, z); 
                            break;
                        case 1:
                            lightlevel = cache.getBlockSkyLight(x+1, y, z); 
                            break;
                        case 2:
                            lightlevel = cache.getBlockSkyLight(x, y+1, z);
                            break;
                        case 3:
                            lightlevel = cache.getBlockSkyLight(x, y, z-1);
                            break;
                    }
                }
            }
            
            switch (seq) {
            case 0:
                x--;
                break;
            case 1:
                y--;
                break;
            case 2:
                z++;
                break;
            case 3:
                y--;
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
                            return;
                        }

                        /* this block is transparent, so recurse */
                        scan(world, x, y, z, seq, isnether, result, cache);

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
}
