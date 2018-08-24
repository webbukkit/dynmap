package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.PlantBlockModel;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

public class PlantBlockModelImpl extends BlockModelImpl implements PlantBlockModel {
    private String patch0;
    
    public PlantBlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        super(blkid, mdf);
        patch0 = mdf.getPatchID(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 100.0, SideVisible.FLIP);
    }
    public PlantBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
        patch0 = mdf.getPatchID(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 100.0, SideVisible.FLIP);
    }


    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;

        return String.format("patchblock:%s,patch0=%s,patch1=%s@90", ids, patch0, patch0);
    }

}
