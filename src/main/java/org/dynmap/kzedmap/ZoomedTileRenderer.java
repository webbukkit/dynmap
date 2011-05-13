package org.dynmap.kzedmap;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.dynmap.Client;
import org.dynmap.MapManager;
import org.dynmap.debug.Debug;

public class ZoomedTileRenderer {
    public ZoomedTileRenderer(Map<String, Object> configuration) {
    }

    public void render(final KzedZoomedMapTile zt, final File outputPath) {
        return;    /* Doing this in Default render, since image already loaded */
    }
}
