package org.dynmap.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * scalable flags primitive - used for keeping track of potentially huge number of tiles
 * 
 * Represents a flag for each tile, with 2D coordinates based on 0,0 origin.  Flags are grouped
 * 64 x 64, represented by an array of 64 longs.  Each set is stored in a hashmap, keyed by a long
 * computed by ((x/64)<<32)+(y/64).
 * 
 */
public class TileFlags {
	private HashMap<Long, long[]> chunkmap = new HashMap<Long, long[]>(); 
	private long last_key = Long.MIN_VALUE;
	private long[] last_row;
	
	public TileFlags() {
	}
	
	public List<String> save() {
	    ArrayList<String> v = new ArrayList<String>();
	    StringBuilder sb = new StringBuilder();
	    for(Map.Entry<Long, long[]> ent : chunkmap.entrySet()) {
	        sb.append(String.format("%x", ent.getKey().longValue()));
	        long[] val = ent.getValue();
	        for(long vv : val) {
	            sb.append(String.format(":%x", vv));
	        }
	        v.add(sb.toString());
	        sb.setLength(0);
	    }
	    return v;
	}
	
	public void load(List<String> vals) {
	    clear();
	    for(String v : vals) {
	        String[] tok = v.split(":");
	        long[] row = new long[64];
	        try {
	            long rowaddr = Long.parseLong(tok[0], 16);
	            for(int i = 0; (i < 64) && (i < (tok.length-1)); i++) {
	                row[i] = Long.parseLong(tok[i+1], 16);
	            }
	            chunkmap.put(rowaddr, row);
	        } catch (NumberFormatException nfx) {
	        }
	    }
	}
	
	public boolean getFlag(int x, int y) {
		long k = (((long)(x >> 6)) << 32) | (0xFFFFFFFFL & (long)(y >> 6));
		long[] row;
		if(k == last_key) {
			row = last_row;
		}
		else {
			row = chunkmap.get(k);
			last_key = k;
			last_row = row;
		}
		if(row == null)
			return false;
		else
			return (row[y & 0x3F] & (1L << (x & 0x3F))) != 0;
	}
	
	public void setFlag(int x, int y, boolean f) {
		long k = (((long)(x >> 6)) << 32) | (0xFFFFFFFFL & (long)(y >> 6));
		long[] row;
		if(k == last_key) {
			row = last_row;
		}
		else {
			row = chunkmap.get(k);
			last_key = k;
			last_row = row;
		}
		if(f) {
			if(row == null) {
				row = new long[64];
				chunkmap.put(k, row);
				last_row = row;
			}
			row[y & 0x3F] |= (1L << (x & 0x3F));
		}
		else {
			if(row != null)
				row[y & 0x3F] &= ~(1L << (x & 0x3F));
		}
	}
	public void clear() {
		chunkmap.clear();
		last_row = null;
		last_key = Long.MIN_VALUE;
	}
}
