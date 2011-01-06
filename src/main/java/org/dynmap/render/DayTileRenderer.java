package org.dynmap.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.bukkit.World;
import org.dynmap.MapManager;
import org.dynmap.MapTile;

public class DayTileRenderer implements MapTileRenderer {
	protected static final Logger log = Logger.getLogger("Minecraft");
	protected String outputPath;
	private Map<Integer, Color[]> colors;
	
	public DayTileRenderer(Map<Integer, Color[]> colors, String outputPath) {
		this.colors = colors;
		this.outputPath = outputPath;
	}
	public void render(MapTile tile) {
		World world = tile.getWorld();
		BufferedImage im = new BufferedImage(MapManager.tileWidth, MapManager.tileHeight, BufferedImage.TYPE_INT_RGB);

		WritableRaster r = im.getRaster();

		int ix = tile.mx;
		int iy = tile.my;
		int iz = tile.mz;
		int jx, jz;

		int x, y;

		/* draw the map */
		for(y=0; y<MapManager.tileHeight;) {
			jx = ix;
			jz = iz;

			for(x=MapManager.tileWidth-1; x>=0; x-=2) {
				Color c1 = scan(world, jx, iy, jz, 0);
				Color c2 = scan(world, jx, iy, jz, 2);

				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x-1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });

				jx++;
				jz++;

			}

			y ++;

			jx = ix;
			jz = iz - 1;

			for(x=MapManager.tileWidth-1; x>=0; x-=2) {
				Color c1 = scan(world, jx, iy, jz, 2);
				jx++;
				jz++;
				Color c2 = scan(world, jx, iy, jz, 0);

				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x-1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });
			}

			y ++;

			ix ++;
			iz --;
		}

		/* save the generated tile */
		saveTile(tile, im);
	}
	
	protected Color scan(World world, int x, int y, int z, int seq)
	{
		for(;;) {
			if(y < 0)
				return Color.BLUE;

			int id = world.getBlockAt(x, y, z).getTypeID();

			switch(seq) {
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

			if(id != 0) {
				Color[] colors = this.colors.get(id);
				if(colors != null) {
					Color c = colors[seq];
					if(c.getAlpha() > 0) {
						/* we found something that isn't transparent! */
						if(c.getAlpha() == 255) {
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
	public void saveTile(MapTile tile, BufferedImage im)
	{
		String tilePath = outputPath
			.replace("{X}", Integer.toString(tile.px))
			.replace("{Y}", Integer.toString(tile.py));
		/* save image */
		try {
			File file = new File(tilePath);
			ImageIO.write(im, "png", file);
		} catch(IOException e) {
			log.log(Level.SEVERE, "Failed to save tile: " + tilePath, e);
		} catch(java.lang.NullPointerException e) {
			log.log(Level.SEVERE, "Failed to save tile (NullPointerException): " + tilePath, e);
		}

		/* now update zoom-out tile */
		/*BufferedImage zIm = mgr.zoomCache.get(zoomPath);

		
		if(zIm == null) {
			// zoom-out tile doesn't exist - try to load it from disk

			mgr.debug("Trying to load zoom-out tile: " + zoomPath);

			try {
				File file = new File(zoomPath);
				zIm = ImageIO.read(file);
			} catch(IOException e) {
			}

			if(zIm == null) {
				mgr.debug("Failed to load zoom-out tile: " + zoomPath);
				// create new one
				// TODO: we might use existing tiles that we could load
				// to fill the zoomed out tile in...
				zIm = new BufferedImage(MapManager.tileWidth, MapManager.tileHeight, BufferedImage.TYPE_INT_RGB);
			} else {
				mgr.debug("Loaded zoom-out tile from " + zoomPath);
			}
		} else {
			mgr.debug("Using zoom-out tile from cache: " + zoomPath);
		}

		// update zoom-out tile

		// scaled size
		int scw = mgr.tileWidth / 2;
		int sch = mgr.tileHeight / 2;

		// origin in zoomed-out tile
		int ox = scw;
		int oy = 0;

		if(zpx != px) ox = 0;
		if(zpy != py) oy = sch;

		// blit scaled rendered tile onto zoom-out tile
		WritableRaster zr = zIm.getRaster();
		Graphics2D g2 = zIm.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(im, ox, oy, scw, sch, null);

		// update zoom-out tile cache
		BufferedImage oldIm = mgr.zoomCache.put(zoomPath, zIm);
		if(oldIm != null && oldIm != zIm) {
			oldIm.flush();
		}

		// save zoom-out tile
		try {
			File file = new File(zoomPath);
			ImageIO.write(zIm, "png", file);
			mgr.debug("saved zoom-out tile at " + zoomPath);

			//log.info("Saved tile: " + path);
		} catch(IOException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile: " + zoomPath, e);
		} catch(java.lang.NullPointerException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile (NullPointerException): " + zoomPath, e);
		}*/
	}
	
	/* try to load already generated image */
	public BufferedImage loadTile(MapTile tile)
	{
		try {
			String path = getPath(tile);
			//log.info("Loading tile from " + path);
			File file = new File(path);
			BufferedImage im = ImageIO.read(file);
			//log.info("OK");
			return im;
		} catch(IOException e) {
			//log.info("failed: " + e.toString());
		}

		return null;
	}
	
	public String getPath(MapTile tile)
	{
		return outputPath
			.replace("{X}", Integer.toString(tile.px))
			.replace("{Y}", Integer.toString(tile.py));
	}
	
	/*
	// generate a path name for this map tile
	public String getPath(MapManager mgr)
	{
		return mgr.tilepath + "t_" + px + "_" + py + ".png";
	}

	// generate a path name for the zoomed-out tile
	public String getZoomPath(MapManager mgr)
	{
		return mgr.tilepath + "zt_" + zpx + "_" + zpy + ".png";
	}

	// generate a path name for this cave map tile
	public String getCavePath(MapManager mgr)
	{
		return mgr.tilepath + "ct_" + px + "_" + py + ".png";
	}

	// generate a path name for the zoomed-out cave tile
	public String getZoomCavePath(MapManager mgr)
	{
		return mgr.tilepath + "czt_" + zpx + "_" + zpy + ".png";
	}
	*/
}
