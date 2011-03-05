package org.dynmap.flat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.Location;
import org.bukkit.World;
import org.dynmap.ColorScheme;
import org.dynmap.DynmapChunk;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.debug.Debug;

public class FlatMap extends MapType {
    private ColorScheme colorScheme;

    public FlatMap(Map<String, Object> configuration) {
        colorScheme = ColorScheme.getScheme((String)configuration.get("colorscheme"));
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
    public boolean render(MapTile tile, File outputFile) {
        FlatMapTile t = (FlatMapTile) tile;
        World w = t.getWorld();

        boolean rendered = false;
        BufferedImage im = new BufferedImage(t.size, t.size, BufferedImage.TYPE_INT_RGB);
        WritableRaster r = im.getRaster();

        for (int x = 0; x < t.size; x++)
            for (int y = 0; y < t.size; y++) {
                int mx = x + t.x * t.size;
                int mz = y + t.y * t.size;
                int my = w.getHighestBlockYAt(mx, mz) - 1;
                int blockType = w.getBlockTypeIdAt(mx, my, mz);
                Color[] colors = colorScheme.colors.get(blockType);
                if (colors == null)
                    continue;
                Color c = colors[0];
                if (c == null)
                    continue;
                r.setPixel(x, y, new int[] {
                    c.getRed(),
                    c.getGreen(),
                    c.getBlue() });
                rendered = true;
            }

        try {
            ImageIO.write(im, "png", outputFile);
        } catch (IOException e) {
            Debug.error("Failed to save image: " + outputFile.getPath(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save image (NullPointerException): " + outputFile.getPath(), e);
        }
        im.flush();
        return rendered;
    }

    public static class FlatMapTile extends MapTile {

        public int x;
        public int y;
        public int size;

        public FlatMapTile(World world, FlatMap map, int x, int y, int size) {
            super(world, map);
            this.x = x;
            this.y = y;
            this.size = size;
        }

        @Override
        public String getFilename() {
            return "flat_" + size + "_" + x + "_" + y + ".png";
        }
    }
}
