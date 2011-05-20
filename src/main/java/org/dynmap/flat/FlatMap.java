package org.dynmap.flat;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

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
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.debug.Debug;
import org.dynmap.MapChunkCache;

public class FlatMap extends MapType {
    private String prefix;
    private ColorScheme colorScheme;
    private int maximumHeight = 127;

    public FlatMap(ConfigurationNode configuration) {
        prefix = (String) configuration.get("prefix");
        colorScheme = ColorScheme.getScheme((String) configuration.get("colorscheme"));
        Object o = configuration.get("maximumheight");
        if (o != null) {
            maximumHeight = Integer.parseInt(String.valueOf(o));
            if (maximumHeight > 127)
                maximumHeight = 127;
        }
    }

    @Override
    public MapTile[] getTiles(Location l) {
        return new MapTile[] { new FlatMapTile(l.getWorld(), this, (int) Math.floor(l.getBlockX() / 128.0), (int) Math.floor(l.getBlockZ() / 128.0), 128) };
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        FlatMapTile t = (FlatMapTile) tile;
        World w = t.getWorld();
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
    public DynmapChunk[] getRequiredChunks(MapTile tile) {
        FlatMapTile t = (FlatMapTile) tile;
        int chunksPerTile = t.size / 16;
        int sx = t.x * chunksPerTile;
        int sz = t.y * chunksPerTile;

        DynmapChunk[] result = new DynmapChunk[chunksPerTile * chunksPerTile];
        int index = 0;
        for (int x = 0; x < chunksPerTile; x++)
            for (int z = 0; z < chunksPerTile; z++) {
                result[index] = new DynmapChunk(sx + x, sz + z);
                index++;
            }
        return result;
    }

    @Override
    public boolean render(MapChunkCache cache, MapTile tile, File outputFile) {
        FlatMapTile t = (FlatMapTile) tile;
        World w = t.getWorld();
        boolean isnether = (w.getEnvironment() == Environment.NETHER) && (maximumHeight == 127);

        boolean rendered = false;
        BufferedImage im = new BufferedImage(t.size, t.size, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = im.getRaster();

        int[] pixel = new int[4];

        for (int x = 0; x < t.size; x++)
            for (int y = 0; y < t.size; y++) {
                int mx = x + t.x * t.size;
                int mz = y + t.y * t.size;
                int my;
                int blockType;
                if(isnether) {
                    /* Scan until we hit air */
                    my = 127;
                    while((blockType = cache.getBlockTypeID(mx, my, mz)) != 0) {
                        my--;
                        if(my < 0) {    /* Solid - use top */
                            my = 127;
                            blockType = cache.getBlockTypeID(mx, my, mz);
                            break;
                        }
                    }
                    if(blockType == 0) {    /* Hit air - now find non-air */
                        while((blockType = cache.getBlockTypeID(mx, my, mz)) == 0) {
                            my--;
                            if(my < 0) {
                                my = 0;
                                break;
                            }
                        }
                    }
                }
                else {
                    my = cache.getHighestBlockYAt(mx, mz) - 1;
                    if(my > maximumHeight) my = maximumHeight;
                    blockType = cache.getBlockTypeID(mx, my, mz);
                }
                byte data = 0;
                Color[] colors = colorScheme.colors[blockType];
                if(colorScheme.datacolors[blockType] != null) {
                    data = cache.getBlockData(mx, my, mz);
                    colors = colorScheme.datacolors[blockType][data];
                }
                if (colors == null)
                    continue;
                Color c = colors[0];
                if (c == null)
                    continue;

                boolean below = my < 64;

                // Make height range from 0 - 1 (1 - 0 for below and 0 - 1 above)
                float height = (below ? 64 - my : my - 64) / 64.0f;

                // Defines the 'step' in coloring.
                float step = 10 / 128.0f;

                // The step applied to height.
                float scale = ((int)(height/step))*step;

                // Make the smaller values change the color (slightly) more than the higher values.
                scale = (float)Math.pow(scale, 1.1f);

                // Don't let the color go fully white or fully black.
                scale *= 0.8f;

                pixel[0] = c.getRed();
                pixel[1] = c.getGreen();
                pixel[2] = c.getBlue();

                if (below) {
                    pixel[0] -= pixel[0] * scale;
                    pixel[1] -= pixel[1] * scale;
                    pixel[2] -= pixel[2] * scale;
                } else {
                    pixel[0] += (255-pixel[0]) * scale;
                    pixel[1] += (255-pixel[1]) * scale;
                    pixel[2] += (255-pixel[2]) * scale;
                }

                raster.setPixel(t.size-y-1, x, pixel);
                rendered = true;
            }
        /* Hand encoding and writing file off to MapManager */
        final File fname = outputFile;
        final MapTile mtile = tile;
        final BufferedImage img = im;
        MapManager.mapman.enqueueImageWrite(new Runnable() {
            public void run() {
                Debug.debug("saving image " + fname.getPath());
                try {
                    ImageIO.write(img, "png", fname);
                } catch (IOException e) {
                    Debug.error("Failed to save image: " + fname.getPath(), e);
                } catch (java.lang.NullPointerException e) {
                    Debug.error("Failed to save image (NullPointerException): " + fname.getPath(), e);
                }
                img.flush();
                MapManager.mapman.pushUpdate(mtile.getWorld(),
                        new Client.Tile(mtile.getFilename()));
            }
        });

        return rendered;
    }

    public static class FlatMapTile extends MapTile {
        FlatMap map;
        public int x;
        public int y;
        public int size;

        public FlatMapTile(World world, FlatMap map, int x, int y, int size) {
            super(world, map);
            this.map = map;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        @Override
        public String getFilename() {
            return map.prefix + "_" + size + "_" + -(y+1) + "_" + x + ".png";
        }
    }
}
