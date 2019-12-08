package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.StairBlockModel;

public class StairBlockModelImpl extends BlockModelImpl implements StairBlockModel {
    
    public StairBlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        super(blkid, mdf);
    }
    public StairBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
    }

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;
        return String.format("customblock:%s,class=org.dynmap.hdmap.renderer.CopyStairBlockRenderer", ids);
    }

}
