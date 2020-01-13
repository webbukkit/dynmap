package org.dynmap.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ForgeConfigFile {
    private File cfg;
    private HashMap<String, String> settings = new HashMap<String, String>();
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
            rdr = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            ArrayList<String> section = new ArrayList<String>();
            String tok = "";
            while((line = rdr.readLine()) != null) {
                boolean skip = false;
                boolean instr = false;
                boolean intok = false;
                tok = "";
                char last_c = ' ';
                int off = line.indexOf(": ");   // If "tok: value style (Minegicka), fix to look normal
                if (off > 0) {
                    line = line.substring(0, off).replace(' ', '_') + "=" + line.substring(off+1);
                }
                for (int i = 0; i < line.length() && !skip; ++i) {
                    char c = line.charAt(i);
                    if(instr) {
                        if(c != '"') {
                            tok += c;
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
                            tok += c;
                        }
                        else {
                            tok = "" + c;
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
                                if(tok.equals("") == false) {
                                    section.add(tok);
                                    tok = "";
                                    instr = intok = false;
                                }
                                break;
                            case '}':
                                if(section.size() > 0) {
                                    section.remove(section.size()-1);
                                }
                                break;
                            case '=':
                                intok = instr = false;
                                String propertyName = tok;
                                tok = "";
                                off = propertyName.indexOf(':');
                                if(off >= 0) {  /* Trim off the Forge 6.4.1+ type prefix */
                                    propertyName = propertyName.substring(off+1);
                                }
                                for(int j = section.size()-1; j >= 0; j--) {
                                    propertyName = section.get(j) + "/" + propertyName;
                                }
                                propertyName = propertyName.replace(' ', '_');
                                settings.put(propertyName, line.substring(i + 1).trim());
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
                try { fis.close(); } catch (IOException iox) {}
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
                } catch (NumberFormatException nfx) {
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
