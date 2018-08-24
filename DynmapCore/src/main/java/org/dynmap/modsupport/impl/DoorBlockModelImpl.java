package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.DoorBlockModel;

public class DoorBlockModelImpl extends BlockModelImpl implements DoorBlockModel {
    
    public DoorBlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        super(blkid, mdf);
    }
    public DoorBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
    }

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;

        return String.format("customblock:%s,class=org.dynmap.hdmap.renderer.DoorRenderer", ids);
    }



}
