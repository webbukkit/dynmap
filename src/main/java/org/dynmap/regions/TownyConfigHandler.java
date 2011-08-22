package org.dynmap.regions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        townblocksize = tcfg.getInt("town_block_size", 16); /* Get block size */        
    }
    /**
     * Get map of attributes for given world
     */
    public Map<String, Object> getRegionData(String wname) {
        Map<String, Object> rslt = new HashMap<String, Object>();
        /* List towns directory - process all towns there */
        File towndir = new File("plugins/Towny/data/towns");
        File[] towns = towndir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".txt");
            }
        });
        for(File town : towns) {
            Map<?,?> td = processTown(town, wname);
            if(td != null) {
                String fn = town.getName();
                rslt.put(fn.substring(0, fn.lastIndexOf('.')), td);
            }
        }
        return rslt;
    }
    
    enum direction { XPLUS, YPLUS, XMINUS, YMINUS };
    
    private static final String FLAGS[] = {
        "hasUpkeep", "pvp", "mobs", "public", "explosion", "fire"
    };
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
        /* If it doesn't start with world, we're done (town on different world) */
        if((blocks == null) || (!blocks.startsWith(wname+":")))
            return null;
        String[] nodes = blocks.split(";"); /* Split into list */
        TileFlags blks = new TileFlags();
        ArrayList<int[]> nodevals = new ArrayList<int[]>();
        int minx = Integer.MAX_VALUE;
        int miny = Integer.MAX_VALUE;
        for(String n: nodes) {
            int idx = n.indexOf(':');
            if(idx >= 0) n = n.substring(idx+1);
            String[] v = n.split(",");
            if(v.length == 2) {
                try {
                    int[] vv = new int[] { Integer.valueOf(v[0]), Integer.valueOf(v[1]) };
                    blks.setFlag(vv[0], vv[1], true);
                    nodevals.add(vv);
                    if(vv[0] < minx) {
                        minx = vv[0];
                        miny = vv[1];
                    }
                    else if((vv[0] == minx) && (vv[1] < miny)) {
                        miny = vv[1];
                    }
                } catch (NumberFormatException nfx) {
                    Log.severe("Error parsing block list in Towny - " + townfile.getPath());
                    return null;
                }
            }
        }
        /* Trace outline of blocks - start from minx, miny going to x+ */
        int init_x = minx;
        int init_y = miny;
        int cur_x = minx+1;
        int cur_y = miny;
        direction dir = direction.XPLUS;
        ArrayList<int[]> linelist = new ArrayList<int[]>();
        linelist.add(new int[] { init_x, init_y } ); // Add start point
        while((cur_x != init_x) || (cur_y != init_y)) {
            switch(dir) {
                case XPLUS: /* Segment in X+ direction */
                    if(!blks.getFlag(cur_x+1, cur_y)) { /* Right turn? */
                        linelist.add(new int[] { cur_x+1, cur_y }); /* Finish line */
                        dir = direction.YPLUS;  /* Change direction */
                    }
                    else if(!blks.getFlag(cur_x+1, cur_y-1)) {  /* Straight? */
                        cur_x++;
                    }
                    else {  /* Left turn */
                        linelist.add(new int[] { cur_x+1, cur_y }); /* Finish line */
                        dir = direction.YMINUS;
                        cur_x++; cur_y--;
                    }
                    break;
                case YPLUS: /* Segment in Y+ direction */
                    if(!blks.getFlag(cur_x, cur_y+1)) { /* Right turn? */
                        linelist.add(new int[] { cur_x+1, cur_y+1 }); /* Finish line */
                        dir = direction.XMINUS;  /* Change direction */
                    }
                    else if(!blks.getFlag(cur_x+1, cur_y+1)) {  /* Straight? */
                        cur_y++;
                    }
                    else {  /* Left turn */
                        linelist.add(new int[] { cur_x+1, cur_y+1 }); /* Finish line */
                        dir = direction.XPLUS;
                        cur_x++; cur_y++;
                    }
                    break;
                case XMINUS: /* Segment in X- direction */
                    if(!blks.getFlag(cur_x-1, cur_y)) { /* Right turn? */
                        linelist.add(new int[] { cur_x, cur_y+1 }); /* Finish line */
                        dir = direction.YMINUS;  /* Change direction */
                    }
                    else if(!blks.getFlag(cur_x-1, cur_y+1)) {  /* Straight? */
                        cur_x--;
                    }
                    else {  /* Left turn */
                        linelist.add(new int[] { cur_x, cur_y+1 }); /* Finish line */
                        dir = direction.YPLUS;
                        cur_x--; cur_y++;
                    }
                    break;
                case YMINUS: /* Segment in Y- direction */
                    if(!blks.getFlag(cur_x, cur_y-1)) { /* Right turn? */
                        linelist.add(new int[] { cur_x, cur_y }); /* Finish line */
                        dir = direction.XPLUS;  /* Change direction */
                    }
                    else if(!blks.getFlag(cur_x-1, cur_y-1)) {  /* Straight? */
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
        rslt = new HashMap<String,Object>();
        rslt.put("points", coordlist);
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
