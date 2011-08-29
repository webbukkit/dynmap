package org.dynmap.regions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.util.config.Configuration;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;
import org.dynmap.utils.TileFlags;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class FactionsConfigHandler {
    private int townblocksize = 16;
    protected JSONParser parser = new JSONParser();    
    private Charset cs_utf8 = Charset.forName("UTF-8");

    public FactionsConfigHandler(ConfigurationNode cfg) {
    }
    /**
     * Get map of attributes for given world
     */
    public Map<String, Object> getRegionData(String wname) {
        /* Load factions.json file */
        File faction = new File("plugins/Factions/factions.json");
        if(faction.canRead() == false) {    /* Can't read config */
            Log.severe("Cannot find Faction file - " + faction.getPath());
            return null;
        }
        JSONObject fact = null;
        try {
            Reader inputFileReader = new InputStreamReader(new FileInputStream(faction), cs_utf8);
            fact = (JSONObject) parser.parse(inputFileReader);
            inputFileReader.close();
        } catch (IOException ex) {
            Log.severe("Exception while reading factions.json.", ex);
        } catch (ParseException ex) {
            Log.severe("Exception while parsing factions.json.", ex);
        }
        if(fact == null)
            return null;
        /* Load board.json */
        File board = new File("plugins/Factions/board.json");
        if(board.canRead() == false) {    /* Can't read config */
            Log.severe("Cannot find Faction file - " + board.getPath());
            return null;
        }
        JSONObject blocks = null;
        try {
            Reader inputFileReader = new InputStreamReader(new FileInputStream(board), cs_utf8);
            blocks = (JSONObject) parser.parse(inputFileReader);
            inputFileReader.close();
        } catch (IOException ex) {
            Log.severe("Exception while reading board.json.", ex);
        } catch (ParseException ex) {
            Log.severe("Exception while parsing board.json.", ex);
        }
        if(blocks == null)
            return null;
        /* Get value from board.json for requested world */
        Object wb = blocks.get(wname);
        if((wb == null) || (!(wb instanceof JSONObject))) {
            return null;
        }
        JSONObject wblocks = (JSONObject)wb;
        Map<String, Object> rslt = new HashMap<String, Object>();
        /* Now go through the factions list, and find outline */
        for(Object factid : fact.keySet()) {
            int fid = 0;
            try {
                fid = Integer.valueOf(factid.toString());
            } catch (NumberFormatException nfx) {
                continue;
            }
            JSONObject fobj = (JSONObject)fact.get(factid); /* Get faction info */
            String town = (String)fobj.get("tag");
            town = ChatColor.stripColor(town);  /* Strip color */
            Map<?,?> td = processFaction(wblocks, fobj, fid);
            if(td != null) {
                rslt.put(town, td);
            }
        }
        return rslt;
    }
    
    enum direction { XPLUS, YPLUS, XMINUS, YMINUS };
    
    private static final String FLAGS[] = {
        "open", "peaceful", "peacefulExplosionsEnabled"
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
    public Map<String,Object> processFaction(JSONObject blocks, JSONObject faction, int factid) {
        /* Build list of nodes matching our faction ID */
        LinkedList<int[]> nodevals = new LinkedList<int[]>();
        TileFlags blks = new TileFlags();
        for(Object k: blocks.keySet()) {
            Object fid = blocks.get(k);
            int bfid = 0;
            try {
                bfid = Integer.valueOf(fid.toString());
            } catch (NumberFormatException nfx) {
                continue;
            }
            if(bfid == factid) {   /* Our faction? */
                String[] coords = k.toString().split(",");
                if(coords.length >= 2) {
                    try {
                        int[] vv = new int[] { Integer.valueOf(coords[0]), Integer.valueOf(coords[1]) };
                        blks.setFlag(vv[0], vv[1], true);
                        nodevals.add(vv);
                    } catch (NumberFormatException nfx) {
                        Log.severe("Error parsing Factions blocks");
                        return null;
                    }
                }
            }
        }
        /* If nothing found for this faction, skip */
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
        HashMap<String,Object> rslt = new HashMap<String,Object>();
        rslt.put("points", polylist);

        /* Add other data */
        Map<String,String> flags = new HashMap<String,String>();
        for(String f : FLAGS) {
            Object fval = faction.get(f);
            if(fval != null) flags.put(f, fval.toString());
        }
        rslt.put("flags", flags);
        
        return rslt;
    }
}
