import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Scanner;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Vector;

import java.io.File;
import java.io.IOException;

import java.awt.Color;

public class MapManager extends Thread {
	protected static final Logger log = Logger.getLogger("Minecraft");

	/* dimensions of a map tile */
	public static final int tileWidth = 128;
	public static final int tileHeight = 128;

	/* lock for our data structures */
	public static final Object lock = new Object();

	/* a hash table of known MapTiles, by their key (projection coords) */
	private HashMap<Long, MapTile> tileStore;

	/* a list of MapTiles to be updated */
	private LinkedList<MapTile> staleTiles;

	/* whether the worker thread should be running now */
	private boolean running = false;

	/* map x, y, z for projection origin */
	public static final int anchorx = 0;
	public static final int anchory = 127;
	public static final int anchorz = 0;

	/* color database: id -> Color */
	public HashMap<Integer, Color[]> colors = null;

	/* path to colors.txt */
	private String colorsetpath = "colors.txt";

	/* path to image tile directory */
	public String tilepath = "tiles/";

	/* time to pause between rendering tiles (ms) */
	public int renderWait = 500;

	/* remember up to this old tile updates (ms) */
	private static final int maxTileAge = 60000;

	/* this list stores the tile updates */
	public LinkedList<TileUpdate> tileUpdates = null;

	/* map debugging mode (send debugging messages to this player) */
	public String debugPlayer = null;

	public void debug(String msg)
	{
		if(debugPlayer == null) return;
		Server s = etc.getServer();
		Player p = s.getPlayer(debugPlayer);
		if(p == null) return;
		p.sendMessage("Map> " + Colors.Red + msg);
	}

	public MapManager()
	{
		/* load configuration */
		PropertiesFile properties;

		properties = new PropertiesFile("server.properties");
		try {
			tilepath = properties.getString("map-tilepath", "tiles/");
			colorsetpath = properties.getString("map-colorsetpath", "colors.txt");
		} catch(Exception ex) {
			log.log(Level.SEVERE, "Exception while reading properties for dynamic map", ex);
		}

		tileStore = new HashMap<Long, MapTile>();
		staleTiles = new LinkedList<MapTile>();
		tileUpdates = new LinkedList<TileUpdate>();



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

	/* initialize and start map manager */
	public void startManager()
	{
		colors = new HashMap<Integer, Color[]>();

		/* load colorset */
		File cfile = new File(colorsetpath);

		try {
			Scanner scanner = new Scanner(cfile);
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

			log.info(nc + " colors loaded from " + colorsetpath);
		} catch(Exception e) {
			log.log(Level.SEVERE, "Failed to load colorset: " + colorsetpath, e);
			return;
		}

		running = true;
		this.start();
		try {
			this.setPriority(MIN_PRIORITY);
			log.info("Set minimum priority for worker thread");
		} catch(SecurityException e) {
			log.info("Failed to set minimum priority for worker thread!");
		}
	}

	/* stop map manager */
	public void stopManager()
	{
		if(!running)
			return;

		log.info("Stopping map renderer...");
		running = false;

		try {
			this.join();
		} catch(InterruptedException e) {
			log.info("Waiting for map renderer to stop is interrupted");
		}
	}

	/* the worker/renderer thread */
	public void run()
	{
		log.info("Map renderer has started.");

		while(running) {

			/*
			if(debugPlayer != null) {
				Player p = etc.getServer().getPlayer(debugPlayer);
				if(p != null) {
					int x = (int) p.getX();
					int y = (int) p.getY();
					int z = (int) p.getZ();
					int dx = x - anchorx;
					int dy = y - anchory;
					int dz = z - anchorz;
					int px = dx + dz;
					int py = dx - dz - dy;

					int tx = tilex(px);
					int ty = tiley(py);

					p.sendMessage(Colors.Red + "pos " + x + "," + y + "," + z + " -> px=" + px + " py=" + py + " -> tx=" + tx + " ty=" + ty);
				}
			}
			*/

			MapTile t = this.popStaleTile();
			if(t != null) {
				t.render(this);

				long now = System.currentTimeMillis();
				long deadline = now - maxTileAge;

				/* update the tileupdate list */
				synchronized(lock) {
					ListIterator<TileUpdate> it = tileUpdates.listIterator(0);
					while(it.hasNext()) {
						TileUpdate tu = it.next();
						if(tu.at < deadline || tu.tile == t)
							it.remove();
					}
					tileUpdates.addLast(new TileUpdate(now, t));
				}

				try {
					this.sleep(renderWait);
				} catch(InterruptedException e) {
				}
			} else {
				try {
					this.sleep(1000);
				} catch(InterruptedException e) {
				}
			}
		}

		log.info("Map renderer has stopped.");
	}

	/* "touch" a block - its map tile will be regenerated */
	public boolean touch(int x, int y, int z)
	{
		int dx = x - anchorx;
		int dy = y - anchory;
		int dz = z - anchorz;
		int px = dx + dz;
		int py = dx - dz - dy;

		int tx = tilex(px);
		int ty = tiley(py);

		boolean r;

		r = pushStaleTile(tx, ty);

		/*
		if(r) {
			debug("touch stale " + x + "," + y + "," + z + " -> px=" + px + " py=" + py + " -> tx=" + tx + " ty=" + ty);
		}
		*/

		boolean ledge = tilex(px - 4) != tx;
		boolean tedge = tiley(py - 4) != ty;
		boolean redge = tilex(px + 4) != tx;
		boolean bedge = tiley(py + 4) != ty;

		if(ledge)
			r = pushStaleTile(tx - tileWidth, ty) || r;
		if(redge)
			r = pushStaleTile(tx + tileWidth, ty) || r;
		if(tedge)
			r = pushStaleTile(tx, ty - tileHeight) || r;
		if(bedge)
			r = pushStaleTile(tx, ty + tileHeight) || r;

		if(ledge && tedge)
			r = pushStaleTile(tx - tileWidth, ty - tileHeight) || r;
		if(ledge && bedge)
			r = pushStaleTile(tx - tileWidth, ty + tileHeight) || r;
		if(redge && tedge)
			r = pushStaleTile(tx + tileWidth, ty - tileHeight) || r;
		if(redge && bedge)
			r = pushStaleTile(tx + tileWidth, ty + tileHeight) || r;

		return r;
	}

	/* get next MapTile that needs to be regenerated, or null
	 * the mapTile is removed from the list of stale tiles! */
	public MapTile popStaleTile()
	{
		synchronized(lock) {
			try {
				MapTile t = staleTiles.removeFirst();
				t.stale = false;
				return t;
			} catch(NoSuchElementException e) {
				return null;
			}
		}
	}

	/* put a MapTile that needs to be regenerated on the list of stale tiles */
	public boolean pushStaleTile(MapTile m)
	{
		synchronized(lock) {
			if(m.stale) return false;

			m.stale = true;
			staleTiles.addLast(m);

			debug(m.toString() + " is now stale");

			return true;
		}
	}

	/* make a MapTile stale by projection position */
	public boolean pushStaleTile(int tx, int ty)
	{
		return pushStaleTile(getTileByPosition(tx, ty));
	}

	/* get (or create) MapTile by projection position */
	private MapTile getTileByPosition(int px, int py)
	{
		Long key = MapTile.key(px, py);
		synchronized(lock) {
			MapTile t = tileStore.get(key);
			if(t == null) {
				/* no maptile exists, need to create one */
				t = new MapTile(px, py);
				tileStore.put(key, t);
				return t;
			} else {
				return t;
			}
		}
	}

	/* return number of stale tiles */
	public int getStaleCount()
	{
		synchronized(lock) {
			return staleTiles.size();
		}
	}

	/* return number of recently updated tiles */
	public int getRecentUpdateCount()
	{
		synchronized(lock) {
			return tileUpdates.size();
		}
	}

	/* regenerate the entire map, starting at position */
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

		Server s = etc.getServer();

		while(open.size() > 0) {
			MapTile t = open.remove(open.size() - 1);
			if(t.stale) continue;
			int h = s.getHighestBlockY(t.mx, t.mz);

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
}
