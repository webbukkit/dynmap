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
import org.dynmap.DynmapChunk;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.debug.Debug;
import org.dynmap.kzedmap.KzedMap;

public class FlatMap extends MapType {
    
    public FlatMap(Map<String, Object> configuration) {
    }
    
    @Override
    public MapTile[] getTiles(Location l) {
        return new MapTile[] {
            new FlatMapTile(l.getWorld(), this, (int)Math.floor(l.getBlockX() / 128.0), (int)Math.floor(l.getBlockZ() / 128.0), 128)
            };
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DynmapChunk[] getRequiredChunks(MapTile tile) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public boolean render(MapTile tile, File outputFile) {
        FlatMapTile t = (FlatMapTile)tile;
        World w = t.getWorld();
        
        BufferedImage im = new BufferedImage(t.size, t.size, BufferedImage.TYPE_INT_RGB);
        WritableRaster r = im.getRaster();
        
        for(int x = 0; x < t.size; x++)
            for (int y = 0; y < t.size; y++) {
                int mx = x+t.x*t.size;
                int mz = y+t.y*t.size;
                int my = w.getHighestBlockYAt(mx, mz) - 1;
                int blockType = w.getBlockTypeIdAt(mx, my, mz);
                Color[] colors = KzedMap.colors.get(blockType);
                if (colors == null)
                    continue;
                Color c = colors[0];
                if (c == null)
                    continue;
                r.setPixel(x, y, new int[] {
                    c.getRed(),
                    c.getGreen(),
                    c.getBlue() });
            }
        
        try {
            ImageIO.write(im, "png", outputFile);
        } catch (IOException e) {
            Debug.error("Failed to save image: " + outputFile.getPath(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save image (NullPointerException): " + outputFile.getPath(), e);
        }
        im.flush();
        return false;
    }

    public class FlatMapTile extends MapTile {
        
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
