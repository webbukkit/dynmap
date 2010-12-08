import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.*;
import java.awt.image.*;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class MapTile {
	protected static final Logger log = Logger.getLogger("Minecraft");

	/* projection position */
	public int px, py;

	/* projection position of zoom-out tile */
	public int zpx, zpy;

	/* minecraft space origin */
	public int mx, my, mz;

	/* whether this tile needs to be updated */
	boolean stale = false;

	/* create new MapTile */
	public MapTile(int px, int py, int zpx, int zpy)
	{
		this.px = px;
		this.py = py;
		this.zpx = zpx;
		this.zpy = zpy;

		mx = MapManager.anchorx + px / 2 + py / 2;
		my = MapManager.anchory;
		mz = MapManager.anchorz + px / 2 - py / 2;
	}

	/* get key by projection position */
	public static long key(int px, int py)
	{
		long lpx = (long) px;
		long lpy = (long) py;

		return ((lpx & (long) 0xffffffffL) << 32) | (lpy & (long) 0xffffffffL);
	}

	/* hash value, based on projection position */
	public int hashCode()
	{
		return (px << 16) ^ py;
	}

	/* equality comparison - based on projection position */
	public boolean equals(MapTile o)
	{
		return o.px == px && o.py == py;
	}

	/* return a simple string representation... */
	public String toString()
	{
		return px + "_" + py;
	}

	/* render this tile */
	public void render(MapManager mgr)
	{
		mgr.debug("Rendering tile: " + this.toString());

		BufferedImage im = new BufferedImage(MapManager.tileWidth, MapManager.tileHeight, BufferedImage.TYPE_INT_RGB);

		WritableRaster r = im.getRaster();

		int ix = mx;
		int iy = my;
		int iz = mz;
		int jx, jz;

		int x, y;

		/* draw the map */
		for(y=0; y<MapManager.tileHeight;) {
			jx = ix;
			jz = iz;

			for(x=MapManager.tileWidth-1; x>=0; x-=2) {
				Color c1 = scan(mgr, jx, iy, jz, 0);
				Color c2 = scan(mgr, jx, iy, jz, 2);

				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x-1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });

				jx++;
				jz++;

			}

			y ++;

			jx = ix;
			jz = iz - 1;

			for(x=MapManager.tileWidth-1; x>=0; x-=2) {
				Color c1 = scan(mgr, jx, iy, jz, 2);
				jx++;
				jz++;
				Color c2 = scan(mgr, jx, iy, jz, 0);

				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x-1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });
			}

			y ++;

			ix ++;
			iz --;
		}

		/* save image */
		try {
			String path = getPath(mgr);
			File file = new File(path);
			ImageIO.write(im, "png", file);
		} catch(IOException e) {
			log.log(Level.SEVERE, "Failed to save tile: " + getPath(mgr), e);
		} catch(java.lang.NullPointerException e) {
			log.log(Level.SEVERE, "Failed to save tile (NullPointerException): " + getPath(mgr), e);
		}

		/* now update zoom-out tile */
		String zPath = getZoomPath(mgr);
		BufferedImage zIm = mgr.zoomCache.get(zPath);

		if(zIm == null) {
			/* zoom-out tile doesn't exist - try to load it from disk */

			mgr.debug("Trying to load zoom-out tile: " + zPath);

			try {
				File file = new File(zPath);
				zIm = ImageIO.read(file);
			} catch(IOException e) {
			}


			if(zIm == null) {
				mgr.debug("Failed to load zoom-out tile: " + zPath);

				/* create new one */
				/* TODO: we might use existing tiles that we could load
				 * to fill the zoomed out tile in... */
				zIm = new BufferedImage(MapManager.tileWidth, MapManager.tileHeight, BufferedImage.TYPE_INT_RGB);
			} else {
				mgr.debug("Loaded zoom-out tile from " + zPath);
			}
		} else {
			mgr.debug("Using zoom-out tile from cache: " + zPath);
		}

		/* update zoom-out tile */

		/* scaled size */
		int scw = mgr.tileWidth / 2;
		int sch = mgr.tileHeight / 2;

		/* origin in zoomed-out tile */
		int ox = scw;
		int oy = 0;

		if(zpx != px) ox = 0;
		if(zpy != py) oy = sch;

		/* blit scaled rendered tile onto zoom-out tile */
		WritableRaster zr = zIm.getRaster();
		Graphics2D g2 = zIm.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(im, ox, oy, scw, sch, null);

		/* update zoom-out tile cache */
		BufferedImage oldIm = mgr.zoomCache.put(zPath, zIm);
		if(oldIm != null && oldIm != zIm) {
			oldIm.flush();
		}

		/* save zoom-out tile */
		try {
			File file = new File(zPath);
			ImageIO.write(zIm, "png", file);
			mgr.debug("saved zoom-out tile at " + zPath);

			//log.info("Saved tile: " + path);
		} catch(IOException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile: " + zPath, e);
		} catch(java.lang.NullPointerException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile (NullPointerException): " + zPath, e);
		}
	}

	/* generate a path name for this map tile */
	public String getPath(MapManager mgr)
	{
		return mgr.tilepath + "t_" + px + "_" + py + ".png";
	}

	/* generate a path name for the zoomed-out tile */
	public String getZoomPath(MapManager mgr)
	{
		return mgr.tilepath + "zt_" + zpx + "_" + zpy + ".png";
	}

	/* try to load already generated image */
	public BufferedImage loadTile(MapManager mgr)
	{
		try {
			String path = getPath(mgr);
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

	/* cast a ray into the map */
	private Color scan(MapManager mgr, int x, int y, int z, int seq)
	{
		Server s = etc.getServer();

		for(;;) {
			if(y < 0)
				return Color.BLUE;

			int id = s.getBlockIdAt(x, y, z);

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
				Color[] colors = mgr.colors.get(id);
				if(colors != null) {
					Color c = colors[seq];
					if(c.getAlpha() > 0) {
						/* we found something that isn't transparent! */
						if(c.getAlpha() == 255) {
							/* it's opaque - the ray ends here */
							return c;
						}

						/* this block is transparent, so recurse */
						Color bg = scan(mgr, x, y, z, seq);

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
}
