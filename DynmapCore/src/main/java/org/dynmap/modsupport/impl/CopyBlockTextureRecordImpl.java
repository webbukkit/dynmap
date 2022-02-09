package org.dynmap.modsupport.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.dynmap.modsupport.CopyBlockTextureRecord;
import org.dynmap.modsupport.TransparencyMode;

public class CopyBlockTextureRecordImpl implements CopyBlockTextureRecord {
    private int[] ids = new int[0];
    private String[] names = new String[0];
    private BitSet meta = null;
    private List<Map<String, String>> blockstates = null;
    private final int srcid;
    private final String srcname;
    private final int srcmeta;
    private final Map<String, String> srcstatemap;
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
        this.srcstatemap = null;
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
        this.srcstatemap = null;
    }

    public CopyBlockTextureRecordImpl(String blkname, String srcname, Map<String, String> srcstatemap) {
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
        this.srcmeta = -1;
        this.srcstatemap = new HashMap<String, String>();
        this.srcstatemap.putAll(srcstatemap);
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
    @Deprecated
    public void setMetaValue(int data) {
    	if (meta == null) {
    		meta = new BitSet();
    	}
    	meta.set(data);
    }

    /**
     * Get matching metadata value mask
     * @return matching metadata mask: bit N is set if given metadata value matches
     */
    @Override
    @Deprecated
    public int getMetaValueMask() {
    	if (meta == null) { return METAMASK_ALL; }
        return (int) meta.toLongArray()[0];	// Only works for 32 flags
    }

    /**
     * Set matching block state mapping
     * Any key-value pairs included must match, while any not included are assumed to match unconditionall
     * @param statemap - map of attribute value pairs
     */
    @Override
    public void setBlockStateMapping(Map<String, String> statemap) {
    	if (blockstates == null) {
    		blockstates = new ArrayList<Map<String, String>>();
    	}
    	Map<String, String> nmap = new HashMap<String, String>();
    	nmap.putAll(statemap);
    	blockstates.add(nmap);
    }
    /**
     * Get all state mappings accumulated for the block model
     */
    @Override
    public List<Map<String, String>> getBlockStateMappings() {
    	return blockstates;
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
        // If we have state data, favor this
        if (this.blockstates != null) {
        	for (Map<String, String> rec : this.blockstates) {
        		if (rec.size() == 0) { continue; }	// Skip if none
        		s += ",state=";
        		boolean first = true;
        		for (Entry<String, String> r : rec.entrySet()) {
        			if (first) {
        				first = false;
        			}
        			else {
        				s += '/';
        			}
        			s += r.getKey() + ":" + r.getValue();
        		}
        	}
        }
        // If we have meta data, add this next
        if (this.meta != null) {
        	for (int i = meta.nextSetBit(0); i != -1; i = meta.nextSetBit(i + 1)) {
        		s += ",data=" + i;
        	}
        }
        // If neither, just state=*
        if ((this.meta == null) && (this.blockstates == null)) {
        	s += ",state=*";
        }
        if (srcname != null) {
            s += ",srcid=%" + srcname;
        }
        else {
            s += ",srcid=" + srcid;
        }
        // If source state, use it
        if (this.srcstatemap != null) {
    		s += ",srcstate=";
    		boolean first = true;
    		for (Entry<String, String> r : this.srcstatemap.entrySet()) {
    			if (first) {
    				first = false;
    			}
    			else {
    				s += '/';
    			}
    			s += r.getKey() + ":" + r.getValue();
    		}
        }
        else {
        	s += ",srcmeta=" + srcmeta;
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
    @Deprecated
    public int getSourceBlockID() {
        return srcid;
    }

    @Override
    @Deprecated
    public int getSourceMeta() {
        return srcmeta;
    }

    /**
     * Get sourc state mappings accumulated for the block model
     */
    @Override
    public Map<String, String> getSourceBlockStateMapping() {
    	return srcstatemap;
    }

    @Override
    public void setTransparencyMode(TransparencyMode mode) {
        this.mode = mode;
    }

    @Override
    public TransparencyMode getTransparencyMode() {
        return mode;
    }
	@Override
	public String getSourceBlockName() {
		return srcname;
	}
}
