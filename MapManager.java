import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

public class MapManager extends Thread {
	protected static final Logger log = Logger.getLogger("Minecraft");

	/* dimensions of a map tile */
	public static final int tileWidth = 128;
	public static final int tileHeight = 128;

	/* (logical!) dimensions of a zoomed out map tile
	 * must be twice the size of the normal tile */
	public static final int zTileWidth = 256;
	public static final int zTileHeight = 256;

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

	/* path to markers file */
	public String markerpath = "markers.txt";
	
	/* port to run web server on */
	public int serverport = 8123;
	
	/* time to pause between rendering tiles (ms) */
	public int renderWait = 500;

	/* remember up to this old tile updates (ms) */
	private static final int maxTileAge = 60000;

	/* this list stores the tile updates */
	public LinkedList<TileUpdate> tileUpdates = null;

	/* map debugging mode (send debugging messages to this player) */
	public String debugPlayer = null;
	
	/* hashmap of markers */
	public HashMap<String,MapMarker> markers = null;

	/* cache this many zoomed-out tiles */
	public static final int zoomCacheSize = 64;

	/* zoomed-out tile cache */
	public Cache<String, BufferedImage> zoomCache;

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
			markerpath = properties.getString("map-markerpath", "markers.txt");
			serverport = Integer.parseInt(properties.getString("map-serverport", "8123"));
		} catch(Exception ex) {
			log.log(Level.SEVERE, "Exception while reading properties for dynamic map", ex);
		}

		tileStore = new HashMap<Long, MapTile>();
		staleTiles = new LinkedList<MapTile>();
		tileUpdates = new LinkedList<TileUpdate>();
		zoomCache = new Cache<String, BufferedImage>(zoomCacheSize);
		
		markers = new HashMap<String,MapMarker>();
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

	/* initialize and start map manager */
	public void startManager()
	{
		colors = new HashMap<Integer, Color[]>();

		/* load colorset */
		File cfile = new File(colorsetpath);

		loadMapMarkers();
		
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

				t = new MapTile(px, py, ztilex(px), ztiley(py));
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

	/* regenerate all zoom tiles, starting at position */
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

	/* regenerate zoom-out tile
	 * returns number of valid subtiles */
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
		/* save zoom-out tile */
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
	
	/* adds a marker to the map */
	public boolean addMapMarker(Player player, String name, double px, double py, double pz)
	{
		if (markers.containsKey(name))
		{
			player.sendMessage("Map> " + Colors.Red + "Marker \"" + name + "\" already exists.");
			return false;
		}
		
		MapMarker marker = new MapMarker();
		marker.name = name;
		marker.owner = player.getName();
		marker.px = px;
		marker.py = py;
		marker.pz = pz;
		markers.put(name, marker);
		
		try
		{
			saveMapMarkers();
			return true;
		}
		catch(IOException e)
		{
			log.log(Level.SEVERE, "Failed to save markers.txt", e);
		}
		
		return false;
	}
	
	/* removes a marker from the map */
	public boolean removeMapMarker(Player player, String name)
	{
		if (markers.containsKey(name))
		{
			MapMarker marker = markers.get(name);
			if (marker.owner.equalsIgnoreCase(player.getName()))
			{
				markers.remove(name);
				
				try
				{
					saveMapMarkers();
					return true;
				}
				catch(IOException e)
				{
					log.log(Level.SEVERE, "Failed to save markers.txt", e);
				}
			}
			else
			{
				player.sendMessage("Map> " + Colors.Red + "Marker \"" + name + "\" does not belong to you.");
			}
		}
		else
		{
			player.sendMessage("Map> " + Colors.Red + "Marker \"" + name + "\" does not exist.");
		}
		
		return false;
	}
	
	/* teleports a user to a marker */
	public boolean teleportToMapMarker(Player player, String name)
	{
		if (markers.containsKey(name))
		{
			MapMarker marker = markers.get(name);

			player.teleportTo(marker.px, marker.py, marker.pz, 0, 0);
		}
		else
		{
			player.sendMessage("Map> " + Colors.Red + "Marker \"" + name + "\" does not exist.");
		}
		
		return false;
	}
	
	/* load the map marker file */
	private void loadMapMarkers()
	{
		Scanner scanner = null;
	    try
	    {
	    	scanner = new Scanner(new FileInputStream(markerpath), "UTF-8");
	    	while (scanner.hasNextLine())
	    	{
	    		String line = scanner.nextLine();
	    		String[] values = line.split(":");
				// If user has old style of file (CSV)
				if (values.length != 5)
				{
					values = line.split(",");
				}
				
				if (values.length == 5)
				{
					MapMarker marker = new MapMarker();
					marker.name = values[0];
					marker.owner = values[1];
					marker.px = Double.parseDouble(values[2]);
					marker.py = Double.parseDouble(values[3]);
					marker.pz = Double.parseDouble(values[4]);
					markers.put(marker.name, marker);
				}
				else
				{
					log.log(Level.INFO, "Failed to load marker: " + values[0]);
				}
	    	}
	    }
	    catch(FileNotFoundException e)
	    {
	    	// No need to log FileNotFoundException
	    }
	    finally
	    {
	    	if (scanner != null) scanner.close();
	    }
	}
	
	/* save the map marker file */
	private void saveMapMarkers() throws IOException
	{
		Writer out = null;
	    try
	    {
	    	out = new OutputStreamWriter(new FileOutputStream(markerpath), "UTF-8");
	    	Collection<MapMarker> values = markers.values();
	    	Iterator<MapMarker> it = values.iterator();
	    	while(it.hasNext())
	    	{
	    		MapMarker marker = it.next();
	    		String line = marker.name + ":" + marker.owner + ":" + marker.px + ":" +  marker.py + ":" +  marker.pz + "\n";
	    		out.write(line);
	    	}
	    }
	    catch(UnsupportedEncodingException e)
	    {
	    	log.log(Level.SEVERE, "Unsupported encoding", e);
	    }
	    catch(FileNotFoundException e)
	    {
	    	log.log(Level.SEVERE, "markers.txt not found", e);
	    }
	    finally
	    {
	    	if (out != null) out.close();
	    }
	}
	
	/* TODO: Is there a cleaner way to get warps/homes than using custom DataSource classes to expose the protected properties? */
	
	protected List<Warp> loadWarps()
	{
		PropertiesFile props = new PropertiesFile("server.properties");

		List<Warp> warps = null;
		
		if (props.getString("data-source").equals("flatfile")) {
			DMFlatFileSource dataSource = new DMFlatFileSource();
			dataSource.initialize();
			dataSource.loadWarps();
			dataSource.loadHomes();
			warps = dataSource.getAllWarps();
		}
		else if (props.getString("data-source").equals("mysql")) {
			DMMySQLSource dataSource = new DMMySQLSource();
			dataSource.initialize();
			dataSource.loadWarps();
			dataSource.loadHomes();
			warps = dataSource.getAllWarps();
		}
		
		return warps;
	}
	
	protected List<Warp> loadHomes()
	{
		PropertiesFile props = new PropertiesFile("server.properties");

		List<Warp> homes = null;
		
		if (props.getString("data-source").equals("flatfile")) {
			DMFlatFileSource dataSource = new DMFlatFileSource();
			dataSource.initialize();
			dataSource.loadWarps();
			dataSource.loadHomes();
			homes = dataSource.getAllHomes();
		}
		else if (props.getString("data-source").equals("mysql")) {
			DMMySQLSource dataSource = new DMMySQLSource();
			dataSource.initialize();
			dataSource.loadWarps();
			dataSource.loadHomes();
			homes = dataSource.getAllHomes();
		}
		
		return homes;
	}
}
