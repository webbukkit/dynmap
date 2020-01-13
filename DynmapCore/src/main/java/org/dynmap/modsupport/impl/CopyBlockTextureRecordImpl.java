package org.dynmap.modsupport.impl;

import java.util.Arrays;

import org.dynmap.DynmapCore;
import org.dynmap.modsupport.CopyBlockTextureRecord;
import org.dynmap.modsupport.TransparencyMode;

public class CopyBlockTextureRecordImpl implements CopyBlockTextureRecord {
    private int[] ids = new int[0];
    private String[] names = new String[0];
    private int metaMask = -1;
    private final int srcid;
    private final String srcname;
    private final int srcmeta;
    private TransparencyMode mode = null;

    private int isNumber(String v) {
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if ((c < '0') || (c > '9'))
                return -1;            
        }
        return Integer.parseInt(v);
    }
    public CopyBlockTextureRecordImpl(int blkid, int srcid, int srcmeta) {
        addBlockID(blkid);
        this.srcid = srcid;
        this.srcname = null;
        this.srcmeta = srcmeta;
    }

    public CopyBlockTextureRecordImpl(String blkname, String srcname, int srcmeta) {
        addBlockName(blkname);
        int id = isNumber(srcname);
        if (id < 0) {
            this.srcname = srcname;
            this.srcid = 0;
        }
        else {
            this.srcname = null;
            this.srcid = id;
        }
        this.srcmeta = srcmeta;
    }

    /**
     * Add block ID to mapping (in case multiple block IDs use same texture mapping)
     * @param blockID - block ID
     */
    @Override
    public void addBlockID(int blockID) {
        if (blockID > 0) {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == blockID) {
                    return;
                }
            }
            ids = Arrays.copyOf(ids, ids.length+1);
            ids[ids.length-1] = blockID;
        }
    }

    /**
     * Add block name to mapping (in case multiple block names use same texture mapping)
     * @param blockname - block name
     */
    @Override
    public void addBlockName(String blockname) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(blockname)) {
                return;
            }
        }
        names = Arrays.copyOf(names, names.length+1);
        names[names.length-1] = blockname;
    }

    /**
     * Get block IDs
     * @return configured IDs
     */
    @Override
    public int[] getBlockIDs() {
        return ids;
    }

    /**
     * Get block names
     * @return configured names
     */
    @Override
    public String[] getBlockNames() {
        return names;
    }

    /**
     * Set metadata value : default is for all values (data=*).  Setting other values will match only the values that are set
     * @param data - value to match (-1 = all, 0-15 is meta value to match)
     */
    @Override
    public void setMetaValue(int data) {
        if (data < 0) { // Setting to all
            metaMask = METAMASK_ALL;
        }
        else if (data < 16) {
            if (metaMask == METAMASK_ALL) {
                metaMask = 0;
            }
            metaMask |= (1 << data);
        }
    }

    /**
     * Get matching metadata value mask
     * @return matching metadata mask: bit N is set if given metadata value matches
     */
    @Override
    public int getMetaValueMask() {
        return metaMask;
    }
    
    public String getLine() {
        if ((ids.length == 0) && (names.length == 0)) {
            return null;
        }
        String s = "copyblock:";
        int idcnt = 0;
        // Add ids
        for (int i = 0; i < ids.length; i++) {
            if (i == 0) {
                s += "id=" + ids[i];
            }
            else {
                s += ",id=" + ids[i];
            }
            idcnt++;
        }
        for (int i = 0; i < names.length; i++) {
            if (idcnt > 0) {
                s += ",";
            }
            s += "id=%" + names[i];
            idcnt++;
        }
        // Add meta
        if (this.metaMask == METAMASK_ALL) {
            s += ",data=*";
        }
        else {
            for (int i = 0; i < 16; i++) {
                if ((metaMask & (1 << i)) != 0) {
                    s += ",data=" + i;
                }
            }
        }
        if (srcname != null) {
            s += ",srcid=%" + srcname + ",srcmeta=" + srcmeta;
        }
        else {
            s += ",srcid=" + srcid + ",srcmeta=" + srcmeta;
        }
        switch (this.mode) {
            case TRANSPARENT:
                s += ",transparency=TRANSPARENT";
                break;
            case SEMITRANSPARENT:
                s += ",transparency=SEMITRANSPARENT";
                break;
            default:
                break;
        }
        return s;
    }

    @Override
    public int getSourceBlockID() {
        return srcid;
    }

    @Override
    public int getSourceMeta() {
        return srcmeta;
    }

    @Override
    public void setTransparencyMode(TransparencyMode mode) {
        this.mode = mode;
    }

    @Override
    public TransparencyMode getTransparencyMode() {
        return mode;
    }
}
