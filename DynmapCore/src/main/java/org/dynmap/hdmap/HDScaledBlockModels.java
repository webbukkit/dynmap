package org.dynmap.hdmap;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinition;

public class HDScaledBlockModels {
    private short[][] modelvectors;
    // These are scale invariant - only need once
    private static PatchDefinition[][] patches;
    private static CustomBlockModel[] custom;

    public HDScaledBlockModels(int scale) {
        short[][] blockmodels = new short[DynmapBlockState.getGlobalIndexMax()][];
        PatchDefinition[][] newpatches = null;
        if (patches == null) { 
        	newpatches = new PatchDefinition[DynmapBlockState.getGlobalIndexMax()][];
        	patches = newpatches;
        }
        CustomBlockModel[] newcustom = null;
        if (custom == null) {
        	newcustom = new CustomBlockModel[DynmapBlockState.getGlobalIndexMax()];
        	custom = newcustom;
        }
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
            	if (newpatches != null) {
            		HDBlockPatchModel pm = (HDBlockPatchModel)m;
            		newpatches[gidx] = pm.getPatches();
            	}
            }
            else if(m instanceof CustomBlockModel) {
            	if (newcustom != null) {
            		CustomBlockModel cbm = (CustomBlockModel)m;
            		newcustom[gidx] = cbm;
            	}
            }
        }
        this.modelvectors = blockmodels;
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
