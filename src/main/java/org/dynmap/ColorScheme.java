package org.dynmap;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dynmap.debug.Debug;

public class ColorScheme {
    protected static final Logger log = Logger.getLogger("Minecraft");
    private static final HashMap<String, ColorScheme> cache = new HashMap<String, ColorScheme>();
    
    public String name;
    /* Switch to arrays - faster than map */ 
    public Color[][] colors;    /* [blk-type][step] */
    public Color[][][] datacolors; /* [bkt-type][blk-dat][step] */

    public ColorScheme(String name, Color[][] colors, Color[][][] datacolors) {
        this.name = name;
        this.colors = colors;
        this.datacolors = datacolors;
    }
    
    private static File getColorSchemeDirectory() {
        return new File(DynmapPlugin.dataDirectory, "colorschemes");
    }
    
    public static ColorScheme getScheme(String name) {
        if (name == null)
            name = "default";
        ColorScheme scheme = cache.get(name);
        if (scheme == null) {
            scheme = loadScheme(name);
            cache.put(name, scheme);
        }
        return scheme;
    }
    
    public static ColorScheme loadScheme(String name) {
        File colorSchemeFile = new File(getColorSchemeDirectory(), name + ".txt");
        Color[][] colors = new Color[256][];
        Color[][][] datacolors = new Color[256][][];
        InputStream stream;
        boolean enab_datacolor = MapManager.mapman.doSyncRender();
        try {
            Debug.debug("Loading colors from '" + colorSchemeFile + "'...");
            stream = new FileInputStream(colorSchemeFile);

            Scanner scanner = new Scanner(stream);
            int nc = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.equals("")) {
                    continue;
                }

                String[] split = line.split("\t");
                if (split.length < 17) {
                    continue;
                }
                Integer id;
                Integer dat = null;
                int idx = split[0].indexOf(':');
                if(idx > 0) {    /* ID:data - data color */
                    id = new Integer(split[0].substring(0, idx));
                    dat = new Integer(split[0].substring(idx+1));
                }
                else {
                    id = new Integer(split[0]);
                }
                Color[] c = new Color[4];

                /* store colors by raycast sequence number */
                c[0] = new Color(Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
                c[3] = new Color(Integer.parseInt(split[5]), Integer.parseInt(split[6]), Integer.parseInt(split[7]), Integer.parseInt(split[8]));
                c[1] = new Color(Integer.parseInt(split[9]), Integer.parseInt(split[10]), Integer.parseInt(split[11]), Integer.parseInt(split[12]));
                c[2] = new Color(Integer.parseInt(split[13]), Integer.parseInt(split[14]), Integer.parseInt(split[15]), Integer.parseInt(split[16]));

                if(dat != null) {
                    if(enab_datacolor) {
                        Color[][] dcolor = datacolors[id];    /* Existing list? */
                        if(dcolor == null) {
                            dcolor = new Color[16][];            /* Make 16 index long list */
                            datacolors[id] = dcolor;
                        }
                        if((dat >= 0) && (dat < 16)) {            /* Add color to list */
                            dcolor[dat] = c;
                        }
                    }
                    if(dat == 0) {    /* Index zero is base color too */
                        colors[id] = c;
                    }
                }
                else {
                    colors[id] = c;
                }
                nc += 1;
            }
            scanner.close();
            /* Last, push base color into any open slots in data colors list */
            for(int k = 0; k < 256; k++) {
                Color[][] dc = datacolors[k];    /* see if data colors too */
                if(dc != null) {
                    Color[] c = colors[k];
                    for(int i = 0; i < 16; i++) {
                        if(dc[i] == null)
                            dc[i] = c;
                    }
                }
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Could not load colors '" + name + "' ('" + colorSchemeFile + "').", e);
            return null;
        } catch (FileNotFoundException e) {
            log.log(Level.SEVERE, "Could not load colors '" + name + "' ('" + colorSchemeFile + "'): File not found.", e);
        }
        return new ColorScheme(name, colors, datacolors);
    }
}
