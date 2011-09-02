package org.dynmap.regions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.util.config.Configuration;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;
import org.dynmap.utils.TileFlags;

public class TownyConfigHandler {
    private int townblocksize;  /* Size of each block - default is 16x16 */
    
    public TownyConfigHandler(ConfigurationNode cfg) {
        /* Find path to Towny base configuration */
        File cfgfile = new File("plugins/Towny/settings/config.yml");
        if(cfgfile.canRead() == false) {    /* Can't read config */
            Log.severe("Cannot find Towny file - " + cfgfile.getPath());
            return;
        }
        Configuration tcfg = new Configuration(cfgfile);
        tcfg.load();
        org.bukkit.util.config.ConfigurationNode townnode = tcfg.getNode("town");
        String tbsize = "16";
        if(townnode != null)
            tbsize = townnode.getString("town_block_size", "16");
        else
            tbsize = tcfg.getString("town_block_size", "16");
        try {
            townblocksize = Integer.valueOf(tbsize);
        } catch (NumberFormatException nfx) {
            townblocksize = 16;
        }
    }
    /**
     * Get map of attributes for given world
     */
    public Map<String, Object> getRegionData(String wname) {
        Map<String, Object> rslt = new HashMap<String, Object>();
        Properties p = new Properties();
        FileInputStream fis = null;
        /* Read world data for this world */
        try {
            fis = new FileInputStream("plugins/Towny/data/worlds/" + wname + ".txt");    /* Open and load file */
            p.load(fis);
        } catch (IOException iox) {
            Log.severe("Error loading Towny world file " + wname + ".txt");
        } finally {
            if(fis != null) {
                try { fis.close(); } catch (IOException iox) {}
            }
        }
        /* Get towns list for our world */
        String t = p.getProperty("towns", "");
        String towns[] = t.split(",");	/* Split on commas */
        /* List towns directory - process all towns there */
        for(String town : towns) {
        	town = town.trim();
        	if(town.length() == 0) continue;
    		File tfile = new File("plugins/Towny/data/towns/" + town + ".txt");
            Map<?,?> td = processTown(tfile, wname);
            if(td != null) {
                rslt.put(town, td);
            }
        }
        return rslt;
    }
    
    enum direction { XPLUS, YPLUS, XMINUS, YMINUS };
    
    private static final String FLAGS[] = {
        "hasUpkeep", "pvp", "mobs", "public", "explosion", "fire"
    };
    
    /**
     * Find all contiguous blocks, set in target and clear in source
     */
    private int floodFillTarget(TileFlags src, TileFlags dest, int x, int y) {
        int cnt = 0;
        if(src.getFlag(x, y)) { /* Set in src */
            src.setFlag(x, y, false);   /* Clear source */
            dest.setFlag(x, y, true);   /* Set in destination */
            cnt++;
            cnt += floodFillTarget(src, dest, x+1, y); /* Fill adjacent blocks */
            cnt += floodFillTarget(src, dest, x-1, y);
            cnt += floodFillTarget(src, dest, x, y+1);
            cnt += floodFillTarget(src, dest, x, y-1);
        }
        return cnt;
    }
    /**
     * Process data from given town file
     */
    public Map<String,Object> processTown(File townfile, String wname) {
        Properties p = new Properties();
        FileInputStream fis = null;
        Map<String, Object> rslt = null;
        try {
            fis = new FileInputStream(townfile);    /* Open and load file */
            p.load(fis);
        } catch (IOException iox) {
            Log.severe("Error loading Towny file " + townfile.getPath());
        } finally {
            if(fis != null) {
                try { fis.close(); } catch (IOException iox) {}
            }
        }
        /* Get block list */
        String blocks = p.getProperty("townBlocks");
        if(blocks == null)	/* Skip if no blocks */
        	return null;
        String[] nodes = blocks.split(";"); /* Split into list */
        TileFlags blks = new TileFlags();
        LinkedList<int[]> nodevals = new LinkedList<int[]>();
        boolean worldmatch = false;
        
        for(String n: nodes) {
            /* Is world prefix? */
            int idx = n.indexOf(':');
            if(idx >= 0) {
                String w = n.substring(0, idx);
                if(w.startsWith("|")) w = w.substring(1);
                worldmatch = w.equals(wname);   /* See if our world */
                n = n.substring(idx+1); /* Process remainder as coordinate */
            }
            if(!worldmatch) continue;
            
            int bidx = n.indexOf(']');
            if(bidx >= 0) {    /* If 0.75 block type present, skip it (we don't care yet) */
                n = n.substring(bidx+1);
            }
            String[] v = n.split(",");
            if(v.length >= 2) { /* Price in 0.75 is third - don't care :) */
                try {
                    int[] vv = new int[] { Integer.valueOf(v[0]), Integer.valueOf(v[1]) };
                    blks.setFlag(vv[0], vv[1], true);
                    nodevals.add(vv);
                } catch (NumberFormatException nfx) {
                    Log.severe("Error parsing block list in Towny - " + townfile.getPath());
                    return null;
                }
            } else if(n.startsWith("|")){   /* End of list? */
                
            } else {
                Log.severe("Invalid block list format in Towny - " + townfile.getPath());
                return null;
            }
        }
        /* If nothing in this world, skip */
        if(nodevals.size() == 0)
            return null;
        /* Loop through until we don't find more areas */
        ArrayList<Map<String,Integer>[]> polygons = new ArrayList<Map<String,Integer>[]>();
        while(nodevals != null) {
            LinkedList<int[]> ournodes = null;
            LinkedList<int[]> newlist = null;
            TileFlags ourblks = null;
            int minx = Integer.MAX_VALUE;
            int miny = Integer.MAX_VALUE;
            for(int[] node : nodevals) {
                if((ourblks == null) && blks.getFlag(node[0], node[1])) {    /* Node still in map? */
                    ourblks = new TileFlags();  /* Create map for shape */
                    ournodes = new LinkedList<int[]>();
                    floodFillTarget(blks, ourblks, node[0], node[1]);   /* Copy shape */
                    ournodes.add(node); /* Add it to our node list */
                    minx = node[0]; miny = node[1];
                }
                /* If shape found, and we're in it, add to our node list */
                else if((ourblks != null) && (ourblks.getFlag(node[0], node[1]))) {
                    ournodes.add(node);
                    if(node[0] < minx) {
                        minx = node[0]; miny = node[1];
                    }
                    else if((node[0] == minx) && (node[1] < miny)) {
                        miny = node[1];
                    }
                }
                else {  /* Else, keep it in the list for the next polygon */
                    if(newlist == null) newlist = new LinkedList<int[]>();
                    newlist.add(node);
                }
            }
            nodevals = newlist; /* Replace list (null if no more to process) */
            if(ourblks == null) continue;   /* Nothing found, skip to end */
            /* Trace outline of blocks - start from minx, miny going to x+ */
            int init_x = minx;
            int init_y = miny;
            int cur_x = minx;
            int cur_y = miny;
            direction dir = direction.XPLUS;
            ArrayList<int[]> linelist = new ArrayList<int[]>();
            linelist.add(new int[] { init_x, init_y } ); // Add start point
            while((cur_x != init_x) || (cur_y != init_y) || (dir != direction.YMINUS)) {
                switch(dir) {
                    case XPLUS: /* Segment in X+ direction */
                        if(!ourblks.getFlag(cur_x+1, cur_y)) { /* Right turn? */
                            linelist.add(new int[] { cur_x+1, cur_y }); /* Finish line */
                            dir = direction.YPLUS;  /* Change direction */
                        }
                        else if(!ourblks.getFlag(cur_x+1, cur_y-1)) {  /* Straight? */
                            cur_x++;
                        }
                        else {  /* Left turn */
                            linelist.add(new int[] { cur_x+1, cur_y }); /* Finish line */
                            dir = direction.YMINUS;
                            cur_x++; cur_y--;
                        }
                        break;
                    case YPLUS: /* Segment in Y+ direction */
                        if(!ourblks.getFlag(cur_x, cur_y+1)) { /* Right turn? */
                            linelist.add(new int[] { cur_x+1, cur_y+1 }); /* Finish line */
                            dir = direction.XMINUS;  /* Change direction */
                        }
                        else if(!ourblks.getFlag(cur_x+1, cur_y+1)) {  /* Straight? */
                            cur_y++;
                        }
                        else {  /* Left turn */
                            linelist.add(new int[] { cur_x+1, cur_y+1 }); /* Finish line */
                            dir = direction.XPLUS;
                            cur_x++; cur_y++;
                        }
                        break;
                    case XMINUS: /* Segment in X- direction */
                        if(!ourblks.getFlag(cur_x-1, cur_y)) { /* Right turn? */
                            linelist.add(new int[] { cur_x, cur_y+1 }); /* Finish line */
                            dir = direction.YMINUS;  /* Change direction */
                        }
                        else if(!ourblks.getFlag(cur_x-1, cur_y+1)) {  /* Straight? */
                            cur_x--;
                        }
                        else {  /* Left turn */
                            linelist.add(new int[] { cur_x, cur_y+1 }); /* Finish line */
                            dir = direction.YPLUS;
                            cur_x--; cur_y++;
                        }
                        break;
                    case YMINUS: /* Segment in Y- direction */
                        if(!ourblks.getFlag(cur_x, cur_y-1)) { /* Right turn? */
                            linelist.add(new int[] { cur_x, cur_y }); /* Finish line */
                            dir = direction.XPLUS;  /* Change direction */
                        }
                        else if(!ourblks.getFlag(cur_x-1, cur_y-1)) {  /* Straight? */
                            cur_y--;
                        }
                        else {  /* Left turn */
                            linelist.add(new int[] { cur_x, cur_y }); /* Finish line */
                            dir = direction.XMINUS;
                            cur_x--; cur_y--;
                        }
                        break;
                }
            }
            @SuppressWarnings("unchecked")
            Map<String,Integer>[] coordlist = new Map[linelist.size()];
            for(int i = 0; i < linelist.size(); i++) {
                coordlist[i] = new HashMap<String,Integer>();
                coordlist[i].put("x", linelist.get(i)[0] * townblocksize);
                coordlist[i].put("z", linelist.get(i)[1] * townblocksize);
            }
            polygons.add(coordlist);
        }
        @SuppressWarnings("unchecked")
        Map<String,Integer>[][] polylist = new Map[polygons.size()][];
        polygons.toArray(polylist);
        rslt = new HashMap<String,Object>();
        rslt.put("points", polylist);

        /* Add other data */
        String mayor = p.getProperty("mayor");
        if(mayor != null) rslt.put("mayor", mayor);
        String nation = p.getProperty("nation");
        if(nation != null) rslt.put("nation", nation);
        String assistants = p.getProperty("assistants");
        if(assistants != null) rslt.put("assistants", assistants);
        String residents = p.getProperty("residents");
        if(residents != null) rslt.put("residents", residents);
        Map<String,String> flags = new HashMap<String,String>();
        for(String f : FLAGS) {
            String fval = p.getProperty(f);
            if(fval != null) flags.put(f, fval);
        }
        rslt.put("flags", flags);
        
        return rslt;
    }
}
