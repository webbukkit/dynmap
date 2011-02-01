package org.dynmap.kzedmap;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.debug.Debugger;

public class ZoomedTileRenderer {
	protected Debugger debugger;
	
	public ZoomedTileRenderer(Debugger debugger, Map<String, Object> configuration) {
		this.debugger = debugger;
	}
	
	public void render(KzedZoomedMapTile zt, String outputPath) {
		KzedMapTile t = zt.originalTile;
		String zoomPath = new File(new File(outputPath), zt.getName() + ".png").getPath();
		render(t.px, t.py, zt.getTileX(), zt.getTileY(), zt.unzoomedImage, zoomPath);
	}
	
	public void render(int px, int py, int zpx, int zpy, BufferedImage image, String zoomPath) {
		BufferedImage zIm = null;
		debugger.debug("Trying to load zoom-out tile: " + zoomPath);
		try {
			File file = new File(zoomPath);
			zIm = ImageIO.read(file);
		} catch(IOException e) {
		}

		if(zIm == null) {
			/* create new one */
			zIm = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);
			debugger.debug("New zoom-out tile created " + zoomPath);
		} else {
			debugger.debug("Loaded zoom-out tile from " + zoomPath);
		}

		/* update zoom-out tile */

		/* scaled size */
		int scw = KzedMap.tileWidth / 2;
		int sch = KzedMap.tileHeight / 2;

		/* origin in zoomed-out tile */
		int ox = 0;
		int oy = 0;

		if(zpx != px) ox = scw;
		if(zpy != py) oy = sch;

		/* blit scaled rendered tile onto zoom-out tile */
		//WritableRaster zr = zIm.getRaster();
		Graphics2D g2 = zIm.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(image, ox, oy, scw, sch, null);

		image.flush();
		
		/* save zoom-out tile */
		try {
			File file = new File(zoomPath);
			ImageIO.write(zIm, "png", file);
			debugger.debug("Saved zoom-out tile at " + zoomPath);
		} catch(IOException e) {
			debugger.error("Failed to save zoom-out tile: " + zoomPath, e);
		} catch(java.lang.NullPointerException e) {
			debugger.error("Failed to save zoom-out tile (NullPointerException): " + zoomPath, e);
		}
		zIm.flush();
	}
}
