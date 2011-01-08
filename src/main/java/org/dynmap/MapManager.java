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
import org.dynmap.kzedmap.DefaultTileRenderer;
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
		serverport = 8123;
		bindaddress = "0.0.0.0";
		//webPath = "/srv/http/dynmap/";
		webPath = "[JAR]";
		
		map = new KzedMap(this, world, debugger);
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

				staleQueue.onTileUpdated(t);

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
	
	public void invalidateTile(MapTile tile) {
		debugger.debug("invalidating tile " + tile.getName());
		staleQueue.pushStaleTile(tile);
	}
}
