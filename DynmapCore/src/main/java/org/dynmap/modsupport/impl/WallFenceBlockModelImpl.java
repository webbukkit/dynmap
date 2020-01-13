package org.dynmap.modsupport.impl;

import java.util.Arrays;

import org.dynmap.modsupport.WallFenceBlockModel;

public class WallFenceBlockModelImpl extends BlockModelImpl implements WallFenceBlockModel {
    private final FenceType type;
    private int[] linked = new int[0];
    
    public WallFenceBlockModelImpl(int blkid, ModModelDefinitionImpl mdf, FenceType type) {
        super(blkid, mdf);
        this.type = type;
    }
    public WallFenceBlockModelImpl(String blkname, ModModelDefinitionImpl mdf, FenceType type) {
        super(blkname, mdf);
        this.type = type;
    }

    @Override
    public FenceType getFenceType() {
        return type;
    }

    @Override
    public void addLinkedBlockID(int blkid) {
        int len = linked.length;
        linked = Arrays.copyOf(linked,  len+1);
        linked[len] = blkid;
    }

    @Override
    public int[] getLinkedBlockIDs() {
        return linked;
    }

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;

        String line = String.format("customblock:%s,class=org.dynmap.hdmap.renderer.FenceWallBlockRenderer,type=%s", 
            ids, (type == FenceType.FENCE)?"fence":"wall");
        for (int i = 0; i < linked.length; i++) {
            line += "link" + i + "=" + linked[i];
        }
        return line;
    }



}
