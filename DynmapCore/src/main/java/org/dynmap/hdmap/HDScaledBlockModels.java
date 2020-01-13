package org.dynmap.hdmap;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinition;

public class HDScaledBlockModels {
    private short[][] modelvectors;
    private PatchDefinition[][] patches;
    private CustomBlockModel[] custom;

    public HDScaledBlockModels(int scale) {
        short[][] blockmodels = new short[DynmapBlockState.getGlobalIndexMax()][];
        PatchDefinition[][] patches = new PatchDefinition[DynmapBlockState.getGlobalIndexMax()][];
        CustomBlockModel[] custom = new CustomBlockModel[DynmapBlockState.getGlobalIndexMax()];
        
        for(Integer gidx : HDBlockModels.models_by_id_data.keySet()) {
            HDBlockModel m = HDBlockModels.models_by_id_data.get(gidx);
            
            if(m instanceof HDBlockVolumetricModel) {
                HDBlockVolumetricModel vm = (HDBlockVolumetricModel)m;
                short[] smod = vm.getScaledMap(scale);
                /* See if scaled model is full block : much faster to not use it if it is */
                if(smod != null) {
                    boolean keep = false;
                    for(int i = 0; (!keep) && (i < smod.length); i++) {
                        if(smod[i] == 0) keep = true;
                    }
                    if(keep) {
                        blockmodels[gidx] = smod;
                    }
                    else {
                        blockmodels[gidx] = null;
                    }
                }
            }
            else if(m instanceof HDBlockPatchModel) {
                HDBlockPatchModel pm = (HDBlockPatchModel)m;
                patches[gidx] = pm.getPatches();
            }
            else if(m instanceof CustomBlockModel) {
                CustomBlockModel cbm = (CustomBlockModel)m;
                custom[gidx] = cbm;
            }
        }
        
        this.modelvectors = blockmodels;
        this.patches = patches;
        this.custom = custom;
    }
    
    public final short[] getScaledModel(DynmapBlockState blk) {
        short[] m = null;
        try {
            m = modelvectors[blk.globalStateIndex];
        } catch (ArrayIndexOutOfBoundsException aioobx) {
            short[][] newmodels = new short[blk.globalStateIndex+1][];
            System.arraycopy(modelvectors, 0, newmodels, 0, modelvectors.length);
            modelvectors = newmodels;
        }
        return m;
    }
    public PatchDefinition[] getPatchModel(DynmapBlockState blk) {
        PatchDefinition[] p = null;
        try {
            p = patches[blk.globalStateIndex];
        } catch (ArrayIndexOutOfBoundsException aioobx) {
            PatchDefinition[][] newpatches = new PatchDefinition[blk.globalStateIndex+1][];
            System.arraycopy(patches, 0, newpatches, 0, patches.length);
            patches = newpatches;
        }
        return p;
    }
    
    public CustomBlockModel getCustomBlockModel(DynmapBlockState blk) {
        CustomBlockModel m = null;
        try {
            m = custom[blk.globalStateIndex];
        } catch (ArrayIndexOutOfBoundsException aioobx) {
            CustomBlockModel[] newcustom = new CustomBlockModel[blk.globalStateIndex+1];
            System.arraycopy(custom, 0, newcustom, 0, custom.length);
            custom = newcustom;
        }   
        return m;
    }
}
