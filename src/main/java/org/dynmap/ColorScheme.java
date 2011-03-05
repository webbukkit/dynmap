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
    public java.util.Map<Integer, Color[]> colors;

    public ColorScheme(String name, java.util.Map<Integer, Color[]> colors) {
        this.name = name;
        this.colors = colors;
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
        java.util.Map<Integer, Color[]> colors = new HashMap<Integer, Color[]>();
        InputStream stream;
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
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Could not load colors '" + name + "' ('" + colorSchemeFile + "').", e);
            return null;
        } catch (FileNotFoundException e) {
            log.log(Level.SEVERE, "Could not load colors '" + name + "' ('" + colorSchemeFile + "'): File not found.", e);
        }
        return new ColorScheme(name, colors);
    }
}
