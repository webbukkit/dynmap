package org.dynmap.kzedmap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.World;
import org.dynmap.debug.Debug;

public class DefaultTileRenderer implements MapTileRenderer {
    protected static Color translucent = new Color(0, 0, 0, 0);
    private String name;
    protected int maximumHeight = 127;

    @Override
    public String getName() {
        return name;
    }

    public DefaultTileRenderer(Map<String, Object> configuration) {
        name = (String) configuration.get("prefix");
        Object o = configuration.get("maximumheight");
        if (o != null) {
            maximumHeight = Integer.parseInt(String.valueOf(o));
            if (maximumHeight > 127)
                maximumHeight = 127;
        }
    }

    public boolean render(KzedMapTile tile, File outputFile) {
        World world = tile.getWorld();
        BufferedImage im = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);

        WritableRaster r = im.getRaster();
        boolean isempty = true;

        int ix = KzedMap.anchorx + tile.px / 2 + tile.py / 2 - (maximumHeight/2);
        int iy = maximumHeight;
        int iz = KzedMap.anchorz + tile.px / 2 - tile.py / 2 + (maximumHeight/2);

        int jx, jz;

        int x, y;

        /* draw the map */
        for (y = 0; y < KzedMap.tileHeight;) {
            jx = ix;
            jz = iz;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                Color c1 = scan(world, jx, iy, jz, 0);
                Color c2 = scan(world, jx, iy, jz, 2);
                isempty = isempty && c1 == translucent && c2 == translucent;
                r.setPixel(x, y, new int[] {
                    c1.getRed(),
                    c1.getGreen(),
                    c1.getBlue() });
                r.setPixel(x - 1, y, new int[] {
                    c2.getRed(),
                    c2.getGreen(),
                    c2.getBlue() });

                jx++;
                jz++;

            }

            y++;

            jx = ix;
            jz = iz - 1;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                Color c1 = scan(world, jx, iy, jz, 2);
                jx++;
                jz++;
                Color c2 = scan(world, jx, iy, jz, 0);
                isempty = isempty && c1 == translucent && c2 == translucent;
                r.setPixel(x, y, new int[] {
                    c1.getRed(),
                    c1.getGreen(),
                    c1.getBlue() });
                r.setPixel(x - 1, y, new int[] {
                    c2.getRed(),
                    c2.getGreen(),
                    c2.getBlue() });
            }

            y++;

            ix++;
            iz--;
        }

        /* save the generated tile */
        saveImage(im, outputFile);
        im.flush();
        
        tile.file = outputFile;
        ((KzedMap) tile.getMap()).invalidateTile(new KzedZoomedMapTile(world, (KzedMap) tile.getMap(), tile));

        return !isempty;
    }

    protected Color scan(World world, int x, int y, int z, int seq) {
        for (;;) {
            if (y < 0)
                return translucent;

            int id = world.getBlockTypeIdAt(x, y, z);

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
                Color[] colors = KzedMap.colors.get(id);
                if (colors != null) {
                    Color c = colors[seq];
                    if (c.getAlpha() > 0) {
                        /* we found something that isn't transparent! */
                        if (c.getAlpha() == 255) {
                            /* it's opaque - the ray ends here */
                            return c;
                        }

                        /* this block is transparent, so recurse */
                        Color bg = scan(world, x, y, z, seq);

                        int cr = c.getRed();
                        int cg = c.getGreen();
                        int cb = c.getBlue();
                        int ca = c.getAlpha();
                        cr *= ca;
                        cg *= ca;
                        cb *= ca;
                        int na = 255 - ca;

                        return new Color((bg.getRed() * na + cr) >> 8, (bg.getGreen() * na + cg) >> 8, (bg.getBlue() * na + cb) >> 8);
                    }
                }
            }
        }
    }

    /* save rendered tile, update zoom-out tile */
    public void saveImage(BufferedImage im, File outputFile) {
        Debug.debug("saving image " + outputFile.getPath());
        /* save image */
        try {
            ImageIO.write(im, "png", outputFile);
        } catch (IOException e) {
            Debug.error("Failed to save image: " + outputFile.getPath(), e);
        } catch (java.lang.NullPointerException e) {
            Debug.error("Failed to save image (NullPointerException): " + outputFile.getPath(), e);
        }
    }
}
