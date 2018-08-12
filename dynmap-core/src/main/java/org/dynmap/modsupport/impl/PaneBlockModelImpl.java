package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.PaneBlockModel;

public class PaneBlockModelImpl extends BlockModelImpl implements PaneBlockModel {
    
    public PaneBlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        super(blkid, mdf);
    }
    public PaneBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
    }

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;
        return String.format("customblock:%s,class=org.dynmap.hdmap.renderer.PaneRenderer", ids);
    }

}
