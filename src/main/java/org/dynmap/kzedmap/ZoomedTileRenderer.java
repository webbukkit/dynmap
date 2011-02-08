package org.dynmap.kzedmap;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.dynmap.debug.Debugger;

public class ZoomedTileRenderer {
    protected Debugger debugger;

    public ZoomedTileRenderer(Debugger debugger, Map<String, Object> configuration) {
        this.debugger = debugger;
    }

    public void render(KzedZoomedMapTile zt, String outputPath) {
        KzedMapTile originalTile = zt.originalTile;
        int px = originalTile.px;
        int py = originalTile.py;
        int zpx = zt.getTileX();
        int zpy = zt.getTileY();
        
        BufferedImage image = null;
        try {
            image = ImageIO.read(new File(new File(outputPath), originalTile.getName() + ".png"));
        } catch (IOException e) {
            return;
        }
        
        if (image == null) {
            debugger.debug("Could not load original tile, won't render zoom-out tile.");
            return;
        }
        
        BufferedImage zIm = null;
        File zoomFile = new File(new File(outputPath), zt.getName() + ".png");
        try {
            zIm = ImageIO.read(zoomFile);
        } catch (IOException e) {
            return;
        }

        if (zIm == null) {
            /* create new one */
            zIm = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);
            debugger.debug("New zoom-out tile created " + zt.getName());
        } else {
            debugger.debug("Loaded zoom-out tile from " + zt.getName());
        }

        /* update zoom-out tile */

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

        /* blit scaled rendered tile onto zoom-out tile */
        // WritableRaster zr = zIm.getRaster();
        Graphics2D g2 = zIm.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, ox, oy, scw, sch, null);

        image.flush();

        /* save zoom-out tile */
        try {
            ImageIO.write(zIm, "png", zoomFile);
            debugger.debug("Saved zoom-out tile at " + zoomFile.getName());
        } catch (IOException e) {
            debugger.error("Failed to save zoom-out tile: " + zoomFile.getName(), e);
        } catch (java.lang.NullPointerException e) {
            debugger.error("Failed to save zoom-out tile (NullPointerException): " + zoomFile.getName(), e);
        }
        zIm.flush();
    }
}
