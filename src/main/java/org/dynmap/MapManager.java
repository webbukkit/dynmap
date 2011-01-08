package org.dynmap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.*;
import org.dynmap.debug.Debugger;
import org.dynmap.kzedmap.CaveTileRenderer;
import org.dynmap.kzedmap.DayTileRenderer;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.kzedmap.MapTileRenderer;

import javax.imageio.ImageIO;

public class MapManager extends Thread {
	protected static final Logger log = Logger.getLogger("Minecraft");

	private World world;
	private Debugger debugger;
	private org.dynmap.Map map;
	public StaleQueue staleQueue;

	/* lock for our data structures */
	public static final Object lock = new Object();

	/* whether the worker thread should be running now */
	private boolean running = false;

	/* path to colors.txt */
	private String colorsetpath = "colors.txt";

	/* path to image tile directory */
	public String tilepath = "tiles/";
	
	/* web files location */
	public String webPath;
	
	/* bind web server to ip-address */
	public String bindaddress = "0.0.0.0";
	
	/* port to run web server on */
	public int serverport = 8123;
	
	/* time to pause between rendering tiles (ms) */
	public int renderWait = 500;
	
	public void debug(String msg)
	{
		debugger.debug(msg);
	}
	
	public MapManager(World world, Debugger debugger)
	{
		this.world = world;
		this.debugger = debugger;
		this.staleQueue = new StaleQueue();

		tilepath = "/srv/http/dynmap/tiles/";
		colorsetpath = "colors.txt";
		serverport = 8123;
		bindaddress = "0.0.0.0";
		//webPath = "/srv/http/dynmap/";
		webPath = "[JAR]";
		
		Map<Integer, Color[]> colors = loadColorSet(colorsetpath);
		map = new KzedMap(world, staleQueue, new MapTileRenderer[] {
				new DayTileRenderer(debugger, colors, tilepath + "t_{X}_{Y}.png"),
				new CaveTileRenderer(debugger, colors, tilepath + "ct_{X}_{Y}.png")
		});
	}
	
	/* initialize and start map manager */
	public void startManager()
	{
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
			boolean found = false;

			MapTile t = staleQueue.popStaleTile();
			if(t != null) {
				debugger.debug("rendering tile " + t + "...");
				t.getMap().render(t);

				staleQueue.freshenTile(t);

				try {
					this.sleep(renderWait);
				} catch(InterruptedException e) {
				}

				found = true;
			}

			if(!found) {
				try {
					this.sleep(500);
				} catch(InterruptedException e) {
				}
			}
		}

		log.info("Map renderer has stopped.");
	}

	public void touch(int x, int y, int z) {
		map.touch(new Location(world, x, y, z));
	}
	
	public Map<Integer, Color[]> loadColorSet(String colorsetpath) {
		Map<Integer, Color[]> colors = new HashMap<Integer, Color[]>();

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
		}
		return colors;
	}
}
