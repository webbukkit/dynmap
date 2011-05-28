package org.dynmap.flat;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.awt.image.BufferedImage;
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
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.MapChunkCache;
import org.json.simple.JSONObject;

public class FlatMap extends MapType {
    private ConfigurationNode configuration;
    private String prefix;
    private ColorScheme colorScheme;
    private int maximumHeight = 127;
    private int ambientlight = 15;;
    private int shadowscale[] = null;
    private boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */

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
        BufferedImage im = KzedMap.allocateBufferedImage(t.size, t.size);
        BufferedImage im_day = null;
        if(night_and_day)
            im_day = KzedMap.allocateBufferedImage(t.size, t.size);
        Color rslt = new Color();
        int[] pixel = new int[3];
        int[] pixel_day = new int[3];

        MapChunkCache.MapIterator mapiter = cache.getIterator(t.x * t.size, 127, t.y * t.size);
        for (int x = 0; x < t.size; x++) {
            mapiter.initialize(t.x * t.size + x, 127, t.y * t.size);
            for (int y = 0; y < t.size; y++, mapiter.incrementZ()) {
                int blockType;
                if(isnether) {
                    while((blockType = mapiter.getBlockTypeID()) != 0) {
                        mapiter.decrementY();
                        if(mapiter.y < 0) {    /* Solid - use top */
                            mapiter.setY(127);
                            blockType = mapiter.getBlockTypeID();
                            break;
                        }
                    }
                    if(blockType == 0) {    /* Hit air - now find non-air */
                        while((blockType = mapiter.getBlockTypeID()) == 0) {
                            mapiter.decrementY();
                            if(mapiter.y < 0) {
                                mapiter.setY(0);
                                break;
                            }
                        }
                    }
                }
                else {
                    int my = mapiter.getHighestBlockYAt() - 1;
                    if(my > maximumHeight) my = maximumHeight;
                    mapiter.setY(my);
                    blockType = mapiter.getBlockTypeID();
                }
                int data = 0;
                Color[] colors = colorScheme.colors[blockType];
                if(colorScheme.datacolors[blockType] != null) {
                    data = mapiter.getBlockData();
                    colors = colorScheme.datacolors[blockType][data];
                }
                if (colors == null)
                    continue;
                Color c = colors[0];
                if (c == null)
                    continue;

                pixel[0] = c.getRed();
                pixel[1] = c.getGreen();
                pixel[2] = c.getBlue();

                /* If ambient light less than 15, do scaling */
                if((shadowscale != null) && (ambientlight < 15)) {
                    if(mapiter.y < 127) 
                        mapiter.incrementY();
                    if(night_and_day) { /* Use unscaled color for day (no shadows from above) */
                        pixel_day[0] = pixel[0];    
                        pixel_day[1] = pixel[1];
                        pixel_day[2] = pixel[2];
                    }
                    int light = Math.max(ambientlight, mapiter.getBlockEmittedLight());
                    pixel[0] = (pixel[0] * shadowscale[light]) >> 8;
                    pixel[1] = (pixel[1] * shadowscale[light]) >> 8;
                    pixel[2] = (pixel[2] * shadowscale[light]) >> 8;
                }
                else {  /* Only do height keying if we're not messing with ambient light */
                    boolean below = mapiter.y < 64;

                    // Make height range from 0 - 1 (1 - 0 for below and 0 - 1 above)
                    float height = (below ? 64 - mapiter.y : mapiter.y - 64) / 64.0f;

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
                    } else {
                        pixel[0] += (255-pixel[0]) * scale;
                        pixel[1] += (255-pixel[1]) * scale;
                        pixel[2] += (255-pixel[2]) * scale;
                    }
                    if(night_and_day) {
                        pixel_day[0] = pixel[0];
                        pixel_day[1] = pixel[1];
                        pixel_day[2] = pixel[2];
                    }
                        
                }
                rslt.setRGBA(pixel[0], pixel[1], pixel[2], 255);
                im.setRGB(t.size-y-1, x, rslt.getARGB());
                if(night_and_day) {
                    rslt.setRGBA(pixel_day[0], pixel_day[1], pixel_day[2], 255);
                    im_day.setRGB(t.size-y-1, x, rslt.getARGB());
                }
                rendered = true;
            }
        }
        Debug.debug("saving image " + outputFile.getPath());
        try {
            ImageIO.write(im, "png", outputFile);
        } catch (IOException e) {
            Debug.error("Failed to save image: " + outputFile.getPath(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save image (NullPointerException): " + outputFile.getPath(), e);
        }
        KzedMap.freeBufferedImage(im);
        MapManager.mapman.pushUpdate(tile.getWorld(),
                                     new Client.Tile(tile.getFilename()));
        if(night_and_day) {
            File dayfile = new File(outputFile.getParent(), tile.getDayFilename());
            Debug.debug("saving image " + dayfile.getPath());
            try {
                ImageIO.write(im_day, "png", dayfile);
            } catch (IOException e) {
                Debug.error("Failed to save image: " + dayfile.getPath(), e);
            } catch (java.lang.NullPointerException e) {
                Debug.error("Failed to save image (NullPointerException): " + dayfile.getPath(), e);
            }
            KzedMap.freeBufferedImage(im_day);
            MapManager.mapman.pushUpdate(tile.getWorld(),
                                         new Client.Tile(tile.getDayFilename()));
        }
        
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
        @Override
        public String getDayFilename() {
            return map.prefix + "_day_" + size + "_" + -(y+1) + "_" + x + ".png";
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
        a(worldObject, "maps", o);
    }
}
