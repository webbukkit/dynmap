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

	/* whether the cave map of this tile needs to be updated */
	boolean staleCave = false;

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

	/* try to get the server to load the relevant chunks */
	public void loadChunks()
	{
		int x1 = mx - 64;
		int x2 = mx + MapManager.tileWidth / 2 + MapManager.tileHeight / 2;

		int z1 = mz - MapManager.tileHeight / 2;
		int z2 = mz + MapManager.tileWidth / 2 + 64;

		int x, z;
		Server s = etc.getServer();

		for(x=x1; x<x2; x+=16) {
			for(z=z1; z<z2; z+=16) {
				if(!s.isChunkLoaded(x, 0, z)) {
					log.info("map render loading chunk: " + x + ", 0, " + z);

					try {
						s.loadChunk(x, 0, z);
					} catch(Exception e) {
						log.log(Level.SEVERE, "Caught exception from loadChunk!", e);
					}
				}
			}
		}
	}

	/* check if all relevant chunks are loaded */
	public boolean isMapLoaded()
	{
		int x1 = mx - 64;
		int x2 = mx + MapManager.tileWidth / 2 + MapManager.tileHeight / 2;

		int z1 = mz - MapManager.tileHeight / 2;
		int z2 = mz + MapManager.tileWidth / 2 + 64;

		int x, z;
		Server s = etc.getServer();

		for(x=x1; x<x2; x+=16) {
			for(z=z1; z<z2; z+=16) {
				if(!s.isChunkLoaded(x, 0, z)) {
					log.info("chunk not loaded: " + x + ", " + z + " for tile " + this.toString());
					return false;
				}
			}
		}

		return true;
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

		//loadChunks();
		if(!isMapLoaded())
			return;

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

		/* save the generated tile */
		saveTile(getPath(mgr), im, getZoomPath(mgr), mgr);
	}

	/* render cave map for this tile */
	public void renderCave(MapManager mgr)
	{
		mgr.debug("Rendering cave map: " + this.toString());

		//loadChunks();
		if(!isMapLoaded())
			return;

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
				Color c1 = caveScan(mgr, jx, iy, jz, 0);
				Color c2 = caveScan(mgr, jx, iy, jz, 2);

				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x-1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });

				jx++;
				jz++;

			}

			y ++;

			jx = ix;
			jz = iz - 1;

			for(x=MapManager.tileWidth-1; x>=0; x-=2) {
				Color c1 = caveScan(mgr, jx, iy, jz, 2);
				jx++;
				jz++;
				Color c2 = caveScan(mgr, jx, iy, jz, 0);

				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x-1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });
			}

			y ++;

			ix ++;
			iz --;
		}

		/* save the generated tile */
		saveTile(getCavePath(mgr), im, getZoomCavePath(mgr), mgr);
	}

	/* save rendered tile, update zoom-out tile */
	public void saveTile(String tilePath, BufferedImage im, String zoomPath, MapManager mgr)
	{
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
		BufferedImage zIm = mgr.zoomCache.get(zoomPath);

		if(zIm == null) {
			/* zoom-out tile doesn't exist - try to load it from disk */

			mgr.debug("Trying to load zoom-out tile: " + zoomPath);

			try {
				File file = new File(zoomPath);
				zIm = ImageIO.read(file);
			} catch(IOException e) {
			}

			if(zIm == null) {
				mgr.debug("Failed to load zoom-out tile: " + zoomPath);
				/* create new one */
				/* TODO: we might use existing tiles that we could load
				 * to fill the zoomed out tile in... */
				zIm = new BufferedImage(MapManager.tileWidth, MapManager.tileHeight, BufferedImage.TYPE_INT_RGB);
			} else {
				mgr.debug("Loaded zoom-out tile from " + zoomPath);
			}
		} else {
			mgr.debug("Using zoom-out tile from cache: " + zoomPath);
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
		BufferedImage oldIm = mgr.zoomCache.put(zoomPath, zIm);
		if(oldIm != null && oldIm != zIm) {
			oldIm.flush();
		}

		/* save zoom-out tile */
		try {
			File file = new File(zoomPath);
			ImageIO.write(zIm, "png", file);
			mgr.debug("saved zoom-out tile at " + zoomPath);

			//log.info("Saved tile: " + path);
		} catch(IOException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile: " + zoomPath, e);
		} catch(java.lang.NullPointerException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile (NullPointerException): " + zoomPath, e);
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

	/* generate a path name for this cave map tile */
	public String getCavePath(MapManager mgr)
	{
		return mgr.tilepath + "ct_" + px + "_" + py + ".png";
	}

	/* generate a path name for the zoomed-out cave tile */
	public String getZoomCavePath(MapManager mgr)
	{
		return mgr.tilepath + "czt_" + zpx + "_" + zpy + ".png";
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

	/* cast a ray into the caves */
	private Color caveScan(MapManager mgr, int x, int y, int z, int seq)
	{
		Server s = etc.getServer();
		boolean air = true;

		for(;;) {
			if(y < 0)
				return Color.BLACK;

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

			switch(id) {
			case 20:
			case 18:
			case 17:
			case 78:
			case 79:
				id = 0;
				break;
			default:
			}

			if(id != 0) {
				air = false;
				continue;
			}

			if(id == 0 && !air) {
				int cr, cg, cb;
				int mult = 256;

				if(y < 64) {
					cr = 0;
					cg = 64 + y * 3;
					cb = 255 - y * 4;
				} else {
					cr = (y-64) * 4;
					cg = 255;
					cb = 0;
				}

				switch(seq) {
				case 0:
					mult = 224;
					break;
				case 1:
					mult = 256;
					break;
				case 2:
					mult = 192;
					break;
				case 3:
					mult = 160;
					break;
				}

				cr = cr * mult / 256;
				cg = cg * mult / 256;
				cb = cb * mult / 256;

				return new Color(cr, cg, cb);
			}
		}
	}
}
