package org.dynmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Scanner;

import org.dynmap.common.BiomeMap;
import org.dynmap.debug.Debug;

public class ColorScheme {
    private static final HashMap<String, ColorScheme> cache = new HashMap<String, ColorScheme>();

    public String name;
    /* Switch to arrays - faster than map */
    public Color[][] colors;    /* [blk-type][step] */
    public Color[][][] datacolors; /* [bkt-type][blk-dat][step] */
    public final Color[][] biomecolors;   /* [Biome.ordinal][step] */
    public final Color[][] raincolors;  /* [rain * 63][step] */
    public final Color[][] tempcolors;  /* [temp * 63][step] */

    public ColorScheme(String name, Color[][] colors, Color[][][] datacolors, Color[][] biomecolors, Color[][] raincolors, Color[][] tempcolors) {
        this.name = name;
        this.colors = colors;
        this.datacolors = datacolors;
        this.biomecolors = biomecolors;
        this.raincolors = raincolors;
        this.tempcolors = tempcolors;
        //TODO: see if we can fix this for IDs vs names...
//        for(int i = 0; i < colors.length; i++) {
//            int id = MapManager.mapman.getBlockAlias(i);
//            if(id != i) {
//                this.colors[i] = this.colors[id];
//                this.datacolors[i] = this.datacolors[id];
//            }
//        }
    }

    private static File getColorSchemeDirectory(DynmapCore core) {
        return new File(core.getDataFolder(), "colorschemes");
    }

    public static ColorScheme getScheme(DynmapCore core, String name) {
        if (name == null)
            name = "default";
        ColorScheme scheme = cache.get(name);
        if (scheme == null) {
            scheme = loadScheme(core, name);
            cache.put(name, scheme);
        }
        return scheme;
    }

    public static ColorScheme loadScheme(DynmapCore core, String name) {
        File colorSchemeFile = new File(getColorSchemeDirectory(core), name + ".txt");
        Color[][] colors = new Color[4096][];
        Color[][][] datacolors = new Color[4096][][];
        Color[][] biomecolors = new Color[BiomeMap.values().length][];
        Color[][] raincolors = new Color[64][];
        Color[][] tempcolors = new Color[64][];
        
        /* Default the biome color */
        for(int i = 0; i < biomecolors.length; i++) {
            Color[] c = new Color[5];
            int red = 0x80 | (0x40 * ((i >> 0) & 1)) | (0x20 * ((i >> 3) & 1)) | (0x10 * ((i >> 6) & 1));
            int green = 0x80 | (0x40 * ((i >> 1) & 1)) | (0x20 * ((i >> 4) & 1)) | (0x10 * ((i >> 7) & 1));
            int blue = 0x80 | (0x40 * ((i >> 2) & 1)) | (0x20 * ((i >> 5) & 1));
            c[0] = new Color(red, green, blue);
            c[3] = new Color(red*4/5, green*4/5, blue*4/5);
            c[1] = new Color(red/2, green/2, blue/2);
            c[2] = new Color(red*2/5, green*2/5, blue*2/5);
            c[4] = new Color((c[1].getRed()+c[3].getRed())/2, (c[1].getGreen()+c[3].getGreen())/2, (c[1].getBlue()+c[3].getBlue())/2, (c[1].getAlpha()+c[3].getAlpha())/2);
            
            biomecolors[i] = c;
        }
        
        InputStream stream;
        try {
            Debug.debug("Loading colors from '" + colorSchemeFile + "'...");
            stream = new FileInputStream(colorSchemeFile);

            Scanner scanner = new Scanner(stream);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.equals("")) {
                    continue;
                }
                /* Make parser less pedantic - tabs or spaces should be fine */
                String[] split = line.split("[\t ]");
                int cnt = 0;
                for(String s: split) { if(s.length() > 0) cnt++; }
                String[] nsplit = new String[cnt];
                cnt = 0;
                for(String s: split) { if(s.length() > 0) { nsplit[cnt] = s; cnt++; } }
                split = nsplit;
                if (split.length < 17) {
                    continue;
                }
                Integer id;
                Integer dat = null;
                boolean isbiome = false;
                boolean istemp = false;
                boolean israin = false;
                int idx = split[0].indexOf(':');
                if(idx > 0) {    /* ID:data - data color */
                    id = Integer.parseInt(split[0].substring(0, idx));
                    dat = Integer.parseInt(split[0].substring(idx+1));
                }
                else if(split[0].charAt(0) == '[') {    /* Biome color data */
                    String bio = split[0].substring(1);
                    idx = bio.indexOf(']');
                    if(idx >= 0) bio = bio.substring(0, idx);
                    isbiome = true;
                    id = -1;
                    BiomeMap[] bm = BiomeMap.values();
                    for(int i = 0; i < bm.length; i++) {
                        if(bm[i].toString().equalsIgnoreCase(bio)) {
                            id = i;
                            break;
                        }
                        else if(bio.equalsIgnoreCase("BIOME_" + i)) {
                            id = i;
                            break;
                        }
                    }
                    if(id < 0) {    /* Not biome - check for rain or temp */
                        if(bio.startsWith("RAINFALL-")) {
                            try {
                                double v = Double.parseDouble(bio.substring(9));
                                if((v >= 0) && (v <= 1.00)) {
                                    id = (int)(v * 63.0);
                                    israin = true;
                                }
                            } catch (NumberFormatException nfx) {
                            }
                        }
                        else if(bio.startsWith("TEMPERATURE-")) {
                            try {
                                double v = Double.parseDouble(bio.substring(12));
                                if((v >= 0) && (v <= 1.00)) {
                                    id = (int)(v * 63.0);
                                    istemp = true;
                                }
                            } catch (NumberFormatException nfx) {
                            }
                        }
                    }
                }
                else {
                    id = Integer.parseInt(split[0]);
                }
                if((!isbiome) && (id >= colors.length)) {
                    Color[][] newcolors = new Color[id+1][];
                    System.arraycopy(colors, 0, newcolors, 0, colors.length);
                    colors = newcolors;
                    Color[][][] newdatacolors = new Color[id+1][][];
                    System.arraycopy(datacolors, 0, newdatacolors, 0, datacolors.length);
                    datacolors = newdatacolors;
                }
                
                Color[] c = new Color[5];

                /* store colors by raycast sequence number */
                c[0] = new Color(Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
                c[3] = new Color(Integer.parseInt(split[5]), Integer.parseInt(split[6]), Integer.parseInt(split[7]), Integer.parseInt(split[8]));
                c[1] = new Color(Integer.parseInt(split[9]), Integer.parseInt(split[10]), Integer.parseInt(split[11]), Integer.parseInt(split[12]));
                c[2] = new Color(Integer.parseInt(split[13]), Integer.parseInt(split[14]), Integer.parseInt(split[15]), Integer.parseInt(split[16]));
                /* Blended color - for 'smooth' option on flat map */
                c[4] = new Color((c[1].getRed()+c[3].getRed())/2, (c[1].getGreen()+c[3].getGreen())/2, (c[1].getBlue()+c[3].getBlue())/2, (c[1].getAlpha()+c[3].getAlpha())/2);

                if(isbiome) {
                    if(istemp) {
                        tempcolors[id] = c;
                    }
                    else if(israin) {
                        raincolors[id] = c;
                    }
                    else if((id >= 0) && (id < biomecolors.length))
                        biomecolors[id] = c;
                }
                else if(dat != null) {
                    Color[][] dcolor = datacolors[id];    /* Existing list? */
                    if(dcolor == null) {
                        dcolor = new Color[16][];            /* Make 16 index long list */
                        datacolors[id] = dcolor;
                    }
                    if((dat >= 0) && (dat < 16)) {            /* Add color to list */
                        dcolor[dat] = c;
                    }
                    if(dat == 0) {    /* Index zero is base color too */
                        colors[id] = c;
                    }
                }
                else {
                    colors[id] = c;
                }
            }
            scanner.close();
            /* Last, push base color into any open slots in data colors list */
            for(int k = 0; k < datacolors.length; k++) {
                Color[][] dc = datacolors[k];    /* see if data colors too */
                if(dc != null) {
                    Color[] c = colors[k];
                    for(int i = 0; i < 16; i++) {
                        if(dc[i] == null)
                            dc[i] = c;
                    }
                }
            }
            /* And interpolate any missing rain and temperature colors */
            interpolateColorTable(tempcolors);
            interpolateColorTable(raincolors);
        } catch (RuntimeException e) {
            Log.severe("Could not load colors '" + name + "' ('" + colorSchemeFile + "').", e);
            return null;
        } catch (FileNotFoundException e) {
            Log.severe("Could not load colors '" + name + "' ('" + colorSchemeFile + "'): File not found.", e);
        }
        return new ColorScheme(name, colors, datacolors, biomecolors, raincolors, tempcolors);
    }
    
    public static void interpolateColorTable(Color[][] c) {
        int idx = -1;
        for(int k = 0; k < c.length; k++) {
            if(c[k] == null) { /* Missing? */
                if((idx >= 0) && (k == (c.length-1))) { /* We're last - so fill forward from last color */
                    for(int kk = idx+1; kk <= k; kk++) {
                        c[kk] = c[idx];
                    }
                }
                /* Skip - will backfill when we find next color */
            }
            else if(idx == -1) { /* No previous color, just backfill this color */
                for(int kk = 0; kk < k; kk++) {
                    c[kk] = c[k];
                }
                idx = k;    /* This is now last defined color */
            }
            else {  /* Else, interpolate between last idx and this one */
                int cnt = c[k].length;
                for(int kk = idx+1; kk < k; kk++) {
                    double interp = (double)(kk-idx)/(double)(k-idx);
                    Color[] cc = new Color[cnt];
                    for(int jj = 0; jj < cnt; jj++) {
                        cc[jj] = new Color(
                           (int)((1.0-interp)*c[idx][jj].getRed() + interp*c[k][jj].getRed()),
                           (int)((1.0-interp)*c[idx][jj].getGreen() + interp*c[k][jj].getGreen()),
                           (int)((1.0-interp)*c[idx][jj].getBlue() + interp*c[k][jj].getBlue()),
                           (int)((1.0-interp)*c[idx][jj].getAlpha() + interp*c[k][jj].getAlpha()));
                    }
                    c[kk] = cc;
                }
                idx = k;
            }
        }
    }
    public Color[] getRainColor(double rain) {
        int idx = (int)(rain * 63.0);
        if((idx >= 0) && (idx < raincolors.length))
            return raincolors[idx];
        else
            return null;
    }
    public Color[] getTempColor(double temp) {
        int idx = (int)(temp * 63.0);
        if((idx >= 0) && (idx < tempcolors.length))
            return tempcolors[idx];
        else
            return null;
    }
    public void resizeColorArray(int idx) {
        if(idx >= colors.length){
            Color[][] newcolors = new Color[idx+1][];
            System.arraycopy(colors, 0, newcolors, 0, colors.length);
            colors = newcolors;
            Color[][][] newdatacolors = new Color[idx+1][][];
            System.arraycopy(datacolors, 0, newdatacolors, 0, datacolors.length);
            datacolors = newdatacolors;
        }
    }
    public static void reset() {
        cache.clear();
    }
}
