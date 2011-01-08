package org.dynmap.kzedmap;

import org.bukkit.Location;
import org.bukkit.World;
import org.dynmap.Map;
import org.dynmap.MapTile;
import org.dynmap.StaleQueue;

public class KzedMap extends Map {
	MapTileRenderer[] renderers;
	
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
	
	public KzedMap(World world, StaleQueue queue, MapTileRenderer[] renderers) {
		super(world, queue);
		this.renderers = renderers;
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
		invalidateTile(new KzedMapTile(this, "t", px, py, ztilex(px), ztiley(py)));
	}
	
	@Override
	public void render(MapTile tile) {
		KzedMapTile t = (KzedMapTile)tile;
		for(MapTileRenderer renderer : renderers) {
			renderer.render(t);
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
}
