package org.dynmap.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ForgeConfigFile {
    private File cfg;
    private HashMap<String, String> settings = new HashMap<>();
    public static final String ALLOWED_CHARS = "._-:";

    public ForgeConfigFile(File cfgfile) {
        cfg = cfgfile;
    }
    
    public boolean load() {
        settings.clear();
        FileInputStream fis = null;
        BufferedReader rdr = null;
        boolean rslt = true;
        
        try {
            fis = new FileInputStream(cfg);
            rdr = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            String line;
            ArrayList<String> section = new ArrayList<>();
            StringBuilder tok = new StringBuilder();
            while ((line = rdr.readLine()) != null) {
                boolean skip = false;
                boolean instr = false;
                boolean intok = false;
                tok = new StringBuilder();
                char last_c = ' ';
                int off = line.indexOf(": ");   // If "tok: value style (Minegicka), fix to look normal
                if (off > 0) {
                    line = line.substring(0, off).replace(' ', '_') + "=" + line.substring(off + 1);
                }
                for (int i = 0; i < line.length() && !skip; ++i) {
                    char c = line.charAt(i);
                    if(instr) {
                        if(c != '"') {
                            tok.append(c);
                        }
                        else if (last_c == '"') {
                            // Ignore double double-quotes
                        }
                        else {
                            instr = false;
                            intok = false;
                        }
                    }
                    else if(c == '"') {
                        if (last_c != '"')  {
                            intok = instr = true;
                        }
                    }
                    else if(Character.isLetterOrDigit(c) || ALLOWED_CHARS.indexOf(c) != -1) {
                        if(intok) {
                            tok.append(c);
                        }
                        else {
                            tok = new StringBuilder("" + c);
                            intok = true;
                        }
                    }
                    else if (Character.isWhitespace(c)) {
                        if((!instr) && intok) {
                            intok = false;
                        }
                    }
                    else {
                        switch (line.charAt(i)) {
                            case '#':
                                skip = true;
                                instr = intok = false;
                                break;
                            case '{':
                                if (!tok.toString().equals("")) {
                                    section.add(tok.toString());
                                    tok = new StringBuilder();
                                    instr = intok = false;
                                }
                                break;
                            case '}':
                                if (section.size() > 0) {
                                    section.remove(section.size() - 1);
                                }
                                break;
                            case '=':
                                intok = instr = false;
                                StringBuilder propertyName = new StringBuilder(tok.toString());
                                tok = new StringBuilder();
                                off = propertyName.toString().indexOf(':');
                                if (off >= 0) {  /* Trim off the Forge 6.4.1+ type prefix */
                                    propertyName = new StringBuilder(propertyName.substring(off + 1));
                                }
                                for (int j = section.size() - 1; j >= 0; j--) {
                                    propertyName.insert(0, section.get(j) + "/");
                                }
                                propertyName = new StringBuilder(propertyName.toString().replace(' ', '_'));
                                settings.put(propertyName.toString(), line.substring(i + 1).trim());
                                skip = true;
                                break;
                        }
                    }
                    last_c = c;
                }
            }
        } catch (IOException iox) {
            rslt = false;
        } finally {
            if(fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
                fis = null;
            }
        }
        return rslt;
    }
    
    public int getBlockID(String id) {
        int idval = -1;
        String val = settings.get(id);
        if (val == null)
            val = settings.get("block/" + id);  /* Check for "block/" */
        if (val != null) {
            val = val.trim();
            if ((val.length() > 0) && Character.isDigit(val.charAt(0))) {
                try {
                    idval = (int) Double.parseDouble(val);   // Handle floats - used by weird mods like Minegicka for some reason
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return idval;
    }
    public void addBlockIDs(Map<String,Integer> map) {
        for(String k : settings.keySet()) {
            if(k.startsWith("block/")) {
                map.put(k.substring("block/".length()), getBlockID(k));
            }
            else if(k.startsWith("blocks/")) { /* RP2 */
                map.put(k.substring("blocks/".length()), getBlockID(k));
            }
            else if(k.startsWith("item/")) {    /* Item codes? */
                map.put("item_" + k.substring("item/".length()), getBlockID(k));
            }
            else if(k.startsWith("walls/")) {    /* Walls (Fancy Walls) codes? */
                map.put(k, getBlockID(k));
            }
            else if(k.startsWith("world/blocks/")) {    /* XyCraft world/blocks */
                map.put(k.substring("world/".length()), getBlockID(k));
            }
            else {
                map.put(k, getBlockID(k));
            }
        }
    }
}
