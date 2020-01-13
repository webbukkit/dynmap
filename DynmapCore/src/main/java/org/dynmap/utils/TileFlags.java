package org.dynmap.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynmap.Log;
/**
 * scalable flags primitive - used for keeping track of potentially huge number of tiles
 * 
 * Represents a flag for each tile, with 2D coordinates based on 0,0 origin.  Flags are grouped
 * 64 x 64, represented by an array of 64 longs.  Each set is stored in a hashmap, keyed by a long
 * computed by ((x/64)&lt;&lt;32)+(y/64).
 * 
 */
public class TileFlags {
	private HashMap<Long, long[]> chunkmap = new HashMap<Long, long[]>(); 
	private long last_key = Long.MAX_VALUE;
	private long[] last_row;
	private int count; // Number of 1 values
	
	public TileFlags() {
	}
	
	public List<String> save() {
	    ArrayList<String> v = new ArrayList<String>();
	    StringBuilder sb = new StringBuilder();
	    for(Map.Entry<Long, long[]> ent : chunkmap.entrySet()) {
	        long v1 = ent.getKey().longValue();
	        sb.append(String.format("%x/%x", ((v1>>32)&0xFFFFFFFFL), (v1 & 0xFFFFFFFFL) ));
	        long[] val = ent.getValue();
	        for(long vv : val) {
	            sb.append(String.format(":%x/%x", ((vv>>32) & 0xFFFFFFFFL), (vv & 0xFFFFFFFFL)));
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
	            String ss[] = tok[0].split("/");
	            long rowaddr = (Long.parseLong(ss[0], 16)<<32) | Long.parseLong(ss[1],16);
	            for(int i = 0; (i < 64) && (i < (tok.length-1)); i++) {
	                ss = tok[i+1].split("/");
	                row[i] = (Long.parseLong(ss[0], 16)<<32) | Long.parseLong(ss[1],16);
	                count += Long.bitCount(row[i]);
	            }
	            chunkmap.put(rowaddr, row);
	        } catch (NumberFormatException nfx) {
	            Log.info("parse error - " + nfx);
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
	
	public boolean setFlag(int x, int y, boolean f) {
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
		boolean prev = false;
        long mask = (1L << (x & 0x3F));
        int idx = y & 0x3F;
		if(f) {
			if(row == null) {
				row = new long[64];
				chunkmap.put(k, row);
				last_row = row;
			}
			else {
			    prev = (row[idx] & mask) != 0L;
			}
			if(!prev) {
			    row[idx] |= mask;
                count++;
			}
		}
		else {
			if(row != null) {
			    prev = (row[idx] & mask) != 0;
			    if(prev) {
			        row[idx] &= ~(mask);
	                count--;
	                if(row[idx] == 0L) { // All zero in element?
	                    boolean nonzero = false;
	                    for(int i = 0; i < row.length; i++) {
	                        if(row[i] != 0L) {
	                            nonzero = true;
	                            break;
	                        }
	                    }
	                    if(!nonzero) {
	                        chunkmap.remove(k);
	                        last_row = null;
	                        last_key = Long.MAX_VALUE;
	                    }
	                }
			    }
			}
		}
		return prev;
	}
	
	/**
	 * Logical OR - set all flags true that are true in given set
	 * 
	 * @param flags - flags to be ORed with our flags
	 */
	public void union(TileFlags flags) {
	    for(Map.Entry<Long, long[]> es : flags.chunkmap.entrySet()) {
	        Long k = es.getKey();
	        long[] f = chunkmap.get(k);
	        long[] nf = es.getValue();
	        if(f == null) {
	            f = new long[64];
                chunkmap.put(k, f);
	        }
            for(int i = 0; i < f.length; i++) {
                count -= Long.bitCount(f[i]);
                f[i] = f[i] | nf[i];
                count += Long.bitCount(f[i]);
            }
	    }
        last_row = null;
        last_key = Long.MAX_VALUE;
	}
	
	public void clear() {
		chunkmap.clear();
		last_row = null;
		last_key = Long.MAX_VALUE;
		count = 0;
	}
	
    // Number of ones
    public int countFlags() {
        return count;
    }
    
    public Iterator getIterator() {
        return new Iterator();
    }

    public static class TileCoord {
	    public int x;
	    public int y;
	    public TileCoord() {
	        this.x = 0;
	        this.y = 0;
	    }
	    public TileCoord(int x, int y) {
	        this.x = x;
	        this.y = y;
	    }
	    public boolean equals(Object o) {
	        if(o instanceof TileCoord) {
	            TileCoord tc = (TileCoord)o;
	            return (tc.x == this.x) && (tc.y == this.y);
	        }
	        return false;
	    }
	    @Override
	    public int hashCode() {
	        return x ^ (y << 8);
	    }
	}
	
	public class Iterator {
	    private Long[] keySet;
	    private int nextIndex;
	    private int lastKeyIndex;
	    
	    Iterator() {
	        this.keySet = new Long[1];
	        this.nextIndex = 0;
	        this.lastKeyIndex = -1;
	    }
	    public TileFlags iterSource() {
	        return TileFlags.this;
	    }
	    public boolean hasNext() {
	        return !chunkmap.isEmpty();
	    }
	    public boolean next(TileCoord coord) {
	        if(count == 0) {
	            return false;
	        }
	        while(true) {
	            if(lastKeyIndex < 0) {
	                Set<Long> ks = chunkmap.keySet();
                    nextIndex = 0;
                    int kscnt = ks.size();
	                if (kscnt == 0) {
	                    return false;
	                }
	                keySet = ks.toArray(new Long[kscnt]);
                    lastKeyIndex = 0;
	            }
	            for(; lastKeyIndex < keySet.length; lastKeyIndex++) {
	                Long k = keySet[lastKeyIndex];
	                if(k == null) continue;
	                long[] flgs = chunkmap.get(k);
	                if(flgs != null) { /* Scan for next 1 after last index */
	                    for( ; nextIndex < (64*64); nextIndex++) {
	                        int hidx = (nextIndex >> 6) & 0x3F;
	                        int vidx = (nextIndex & 0x3F);
                            if (((flgs[vidx] >> hidx) & 0x1L) != 0L) {
                                coord.x = (int)(((k >> 32) & 0xFFFFFFFFL) << 6) | hidx;
                                coord.y = (int)((k & 0xFFFFFFFFL) << 6) | vidx;
                                nextIndex++;
                                return true;
                            }
	                    }
	                }
	                nextIndex = 0;
	            }
	            lastKeyIndex = -1;
	        }
	    }
	}
}
