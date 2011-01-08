package org.dynmap.kzedmap;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.*;
import java.awt.image.*;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.bukkit.Player;
import org.bukkit.World;
import org.bukkit.Server;
import org.dynmap.MapTile;

public class KzedMapTile extends MapTile {
	protected static final Logger log = Logger.getLogger("Minecraft");

	public KzedMap map;
	
	public MapTileRenderer renderer;
	
	/* projection position */
	public int px, py;

	/* minecraft space origin */
	public int mx, my, mz;

	/* create new MapTile */
	public KzedMapTile(KzedMap map, MapTileRenderer renderer, int px, int py) {
		super(map);
		this.renderer = renderer;
		this.px = px;
		this.py = py;

		mx = KzedMap.anchorx + px / 2 + py / 2;
		my = KzedMap.anchory;
		mz = KzedMap.anchorz + px / 2 - py / 2;
	}
	
	@Override
	public String getName() {
		return renderer.getName() + "_" + px + "_" + py;
	}
	
	/* try to get the server to load the relevant chunks */
	public void loadChunks()
	{
		int x1 = mx - 64;
		int x2 = mx + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

		int z1 = mz - KzedMap.tileHeight / 2;
		int z2 = mz + KzedMap.tileWidth / 2 + 64;

		int x, z;

		for(x=x1; x<x2; x+=16) {
			for(z=z1; z<z2; z+=16) {
				if(!map.getWorld().isChunkLoaded(map.getWorld().getChunkAt(x, z))) {
					log.info("chunk not loaded: " + x + ", 0, " + z);
					/*

					try {
						s.loadChunk(x, 0, z);
					} catch(Exception e) {
						log.log(Level.SEVERE, "Caught exception from loadChunk!", e);
					}*/
				}
			}
		}
	}

	/* check if all relevant chunks are loaded */
	public boolean isMapLoaded()
	{
		int x1 = mx - KzedMap.tileHeight / 2;
		int x2 = mx + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

		int z1 = mz - KzedMap.tileHeight / 2;
		int z2 = mz + KzedMap.tileWidth / 2 + 64;

		int x, z;

		for(x=x1; x<x2; x+=16) {
			for(z=z1; z<z2; z+=16) {
				if(!map.getWorld().isChunkLoaded(map.getWorld().getChunkAt(x, z))) {
					// Will try to load chunk.
					//log.info("chunk not loaded: " + x + ", " + z + " for tile " + this.toString());
					return false;
					
					// Sometimes give very heavy serverload:
                                        /*try {
                                                s.loadChunk(x, 0, z);
                                        } catch(Exception e) {
                                                log.log(Level.SEVERE, "Caught exception from loadChunk!", e);
						return false;
                                        }
					if(!s.isChunkLoaded(x, 0, z)) {
						log.info("Could not load chunk: " + x + ", " + z + " for tile " + this.toString());
						return false;
					}*/
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
		return ((px << 16) ^ py) ^ getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof KzedMapTile) {
			return equals((KzedMapTile)obj);
		}
		return super.equals(obj);
	}
	
	/* equality comparison - based on projection position */
	public boolean equals(KzedMapTile o)
	{
		return o.getName().equals(getName()) && o.px == px && o.py == py;
	}

	/* return a simple string representation... */
	public String toString()
	{
		return getName() + "_" + px + "_" + py;
	}
}
