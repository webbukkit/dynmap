package org.dynmap.kzedmap;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Scanner;

import org.bukkit.Location;
import org.bukkit.World;
import org.dynmap.Map;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.debug.Debugger;

public class KzedMap extends Map {
	/* dimensions of a map tile */
	public static final int tileWidth = 128;
	public static final int tileHeight = 128;
	
	/* (logical!) dimensions of a zoomed out map tile
	 * must be twice the size of the normal tile */
	public static final int zTileWidth = 256;
	public static final int zTileHeight = 256;
	
	/* map x, y, z for projection origin */
	public static final int anchorx = 0;
	public static final int anchory = 127;
	public static final int anchorz = 0;
	
	public static java.util.Map<Integer, Color[]> colors;
	MapTileRenderer[] renderers;
	ZoomedTileRenderer zoomrenderer;
	
	public KzedMap(MapManager manager, World world, Debugger debugger) {
		super(manager, world, debugger);
		if (colors == null) {
			colors = loadColorSet("colors.txt");
		}
		renderers = new MapTileRenderer[] {
				new DefaultTileRenderer("t", debugger),
				new CaveTileRenderer("ct", debugger),
		};
		zoomrenderer = new ZoomedTileRenderer(debugger);
	}
	
	@Override
	public void touch(Location l) {
		int x = l.getBlockX();
		int y = l.getBlockY();
		int z = l.getBlockZ();
		
		int dx = x - anchorx;
		int dy = y - anchory;
		int dz = z - anchorz;
		int px = dx + dz;
		int py = dx - dz - dy;

		int tx = tilex(px);
		int ty = tiley(py);

		invalidateTile(tx, ty);

		boolean ledge = tilex(px - 4) != tx;
		boolean tedge = tiley(py - 4) != ty;
		boolean redge = tilex(px + 4) != tx;
		boolean bedge = tiley(py + 4) != ty;

		if(ledge) invalidateTile(tx - tileWidth, ty);
		if(redge) invalidateTile(tx + tileWidth, ty);
		if(tedge) invalidateTile(tx, ty - tileHeight);
		if(bedge) invalidateTile(tx, ty + tileHeight);

		if(ledge && tedge) invalidateTile(tx - tileWidth, ty - tileHeight);
		if(ledge && bedge) invalidateTile(tx - tileWidth, ty + tileHeight);
		if(redge && tedge) invalidateTile(tx + tileWidth, ty - tileHeight);
		if(redge && bedge) invalidateTile(tx + tileWidth, ty + tileHeight);
	}
	
	public void invalidateTile(int px, int py) {
		for(MapTileRenderer renderer : renderers) {
			invalidateTile(new KzedMapTile(this, renderer, px, py));
		}
	}
	
	@Override
	public void render(MapTile tile) {
		if (tile instanceof KzedZoomedMapTile) {
			zoomrenderer.render((KzedZoomedMapTile)tile, getMapManager().tilepath);
		} else if (tile instanceof KzedMapTile) {
			((KzedMapTile)tile).renderer.render((KzedMapTile)tile, getMapManager().tilepath);
		}
	}
	
	/* tile X for position x */
	static int tilex(int x)
	{
		if(x < 0)
			return x - (tileWidth + (x % tileWidth));
		else
			return x - (x % tileWidth);
	}

	/* tile Y for position y */
	static int tiley(int y)
	{
		if(y < 0)
			return y - (tileHeight + (y % tileHeight));
		else
			return y - (y % tileHeight);
	}

	/* zoomed-out tile X for tile position x */
	static int ztilex(int x)
	{
		if(x < 0)
			return x + x % zTileWidth;
		else
			return x - (x % zTileWidth);
	}

	/* zoomed-out tile Y for tile position y */
	static int ztiley(int y)
	{
		if(y < 0)
			return y + y % zTileHeight;
			//return y - (zTileHeight + (y % zTileHeight));
		else
			return y - (y % zTileHeight);
	}
	
	/*
	// regenerate the entire map, starting at position
	public void regenerate(int x, int y, int z)
	{
		int dx = x - anchorx;
		int dy = y - anchory;
		int dz = z - anchorz;
		int px = dx + dz;
		int py = dx - dz - dy;

		int tx = tilex(px);
		int ty = tiley(py);

		MapTile first = getTileByPosition(tx, ty);

		Vector<MapTile> open = new Vector<MapTile>();
		open.add(first);

		while(open.size() > 0) {
			MapTile t = open.remove(open.size() - 1);
			if(t.stale) continue;
			int h = world.getHighestBlockYAt(t.mx, t.mz);

			log.info("walking: " + t.mx + ", " + t.mz + ", h = " + h);
			if(h < 1)
				continue;

			pushStaleTile(t);

			open.add(getTileByPosition(t.px + tileWidth, t.py));
			open.add(getTileByPosition(t.px - tileWidth, t.py));
			open.add(getTileByPosition(t.px, t.py + tileHeight));
			open.add(getTileByPosition(t.px, t.py - tileHeight));
		}
	}

	// regenerate all zoom tiles, starting at position
	public void regenerateZoom(int x, int y, int z)
	{
		int dx = x - anchorx;
		int dy = y - anchory;
		int dz = z - anchorz;
		int px = dx + dz;
		int py = dx - dz - dy;

		int fzpx = ztilex(tilex(px));
		int fzpy = ztiley(tiley(py));

		class Pair implements Comparator {
			public int x;
			public int y;
			public Pair(int x, int y)
			{
				this.x = x;
				this.y = y;
			}

			public int hashCode()
			{
				return (x << 16) ^ y;
			}

			public boolean equals(Object o)
			{
				Pair p = (Pair) o;
				return x == p.x && y == p.y;
			}

			public int compare(Object o1, Object o2)
			{
				Pair p1 = (Pair) o1;
				Pair p2 = (Pair) o2;
				if(p1.x < p1.x) return -1;
				if(p1.x > p1.x) return 1;
				if(p1.y < p1.y) return -1;
				if(p1.y > p1.y) return 1;
				return 0;
			}
		}

		HashSet<Pair> visited = new HashSet<Pair>();
		Vector<Pair> open = new Vector<Pair>();

		Pair fp = new Pair(fzpx, fzpy);
		open.add(fp);
		visited.add(fp);

		while(open.size() > 0) {
			Pair p = open.remove(open.size() - 1);

			int zpx = p.x;
			int zpy = p.y;

			log.info("Regenerating zoom tile " + zpx + "," + zpy);

			int g = regenZoomTile(zpx, zpy);

			if(g > 0) {
				Pair[] np = new Pair[4];
				np[0] = new Pair(zpx-zTileWidth, zpy);
				np[1] = new Pair(zpx+zTileWidth, zpy);
				np[2] = new Pair(zpx, zpy-zTileHeight);
				np[3] = new Pair(zpx, zpy+zTileHeight);

				for(int i=0; i<4; i++) {
					if(!visited.contains(np[i])) {
						visited.add(np[i]);
						open.add(np[i]);
					}
				}
			}
		}
	}

	// regenerate zoom-out tile
	// returns number of valid subtiles
	public int regenZoomTile(int zpx, int zpy)
	{
		int px1 = zpx + tileWidth;
		int py1 = zpy;
		int px2 = zpx;
		int py2 = py1 + tileHeight;

		MapTile t1 = getTileByPosition(px1, py1);
		MapTile t2 = getTileByPosition(px2, py1);
		MapTile t3 = getTileByPosition(px1, py2);
		MapTile t4 = getTileByPosition(px2, py2);

		BufferedImage im1 = t1.loadTile(this);
		BufferedImage im2 = t2.loadTile(this);
		BufferedImage im3 = t3.loadTile(this);
		BufferedImage im4 = t4.loadTile(this);

		BufferedImage zIm = new BufferedImage(MapManager.tileWidth, MapManager.tileHeight, BufferedImage.TYPE_INT_RGB);
		WritableRaster zr = zIm.getRaster();
		Graphics2D g2 = zIm.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		int scw = tileWidth / 2;
		int sch = tileHeight / 2;

		int good = 0;

		if(im1 != null) {
			g2.drawImage(im1, 0, 0, scw, sch, null);
			good ++;
		}

		if(im2 != null) {
			g2.drawImage(im2, scw, 0, scw, sch, null);
			good ++;
		}

		if(im3 != null) {
			g2.drawImage(im3, 0, sch, scw, sch, null);
			good ++;
		}

		if(im4 != null) {
			g2.drawImage(im4, scw, sch, scw, sch, null);
			good ++;
		}

		if(good == 0) {
			return 0;
		}

		String zPath = t1.getZoomPath(this);
		// save zoom-out tile
		try {
			File file = new File(zPath);
			ImageIO.write(zIm, "png", file);
			log.info("regenZoomTile saved zoom-out tile at " + zPath);
		} catch(IOException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile: " + zPath, e);
		} catch(java.lang.NullPointerException e) {
			log.log(Level.SEVERE, "Failed to save zoom-out tile (NullPointerException): " + zPath, e);
		}

		return good;
	}
	*/
	
	public java.util.Map<Integer, Color[]> loadColorSet(String colorsetpath) {
		java.util.Map<Integer, Color[]> colors = new HashMap<Integer, Color[]>();

		InputStream stream;

		try {
			/* load colorset */
			File cfile = new File(colorsetpath);
			if (cfile.isFile()) {
				getDebugger().debug("Loading colors from '" + colorsetpath + "'...");
				stream = new FileInputStream(cfile);
			} else {
				getDebugger().debug("Loading colors from jar...");
				stream = KzedMap.class.getResourceAsStream("/colors.txt");
			}
			
			Scanner scanner = new Scanner(stream);
			int nc = 0;
			while(scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (line.startsWith("#") || line.equals("")) {
					continue;
				}

				String[] split = line.split("\t");
				if (split.length < 17) {
					continue;
				}

				Integer id = new Integer(split[0]);

				Color[] c = new Color[4];

				/* store colors by raycast sequence number */
				c[0] = new Color(Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
				c[3] = new Color(Integer.parseInt(split[5]), Integer.parseInt(split[6]), Integer.parseInt(split[7]), Integer.parseInt(split[8]));
				c[1] = new Color(Integer.parseInt(split[9]), Integer.parseInt(split[10]), Integer.parseInt(split[11]), Integer.parseInt(split[12]));
				c[2] = new Color(Integer.parseInt(split[13]), Integer.parseInt(split[14]), Integer.parseInt(split[15]), Integer.parseInt(split[16]));

				colors.put(id, c);
				nc += 1;
			}
			scanner.close();
		} catch(Exception e) {
			getDebugger().error("Could not load colors", e);
			return null;
		}
		return colors;
	}
}
