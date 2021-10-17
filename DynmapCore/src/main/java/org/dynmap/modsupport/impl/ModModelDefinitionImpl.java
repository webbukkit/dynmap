package org.dynmap.modsupport.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.modsupport.BoxBlockModel;
import org.dynmap.modsupport.CuboidBlockModel;
import org.dynmap.modsupport.DoorBlockModel;
import org.dynmap.modsupport.ModModelDefinition;
import org.dynmap.modsupport.ModTextureDefinition;
import org.dynmap.modsupport.PaneBlockModel;
import org.dynmap.modsupport.PatchBlockModel;
import org.dynmap.modsupport.PlantBlockModel;
import org.dynmap.modsupport.StairBlockModel;
import org.dynmap.modsupport.VolumetricBlockModel;
import org.dynmap.modsupport.WallFenceBlockModel;
import org.dynmap.modsupport.WallFenceBlockModel.FenceType;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

public class ModModelDefinitionImpl implements ModModelDefinition {
    private final ModTextureDefinitionImpl txtDef;
    private boolean published = false;
    private ArrayList<BlockModelImpl> blkModel = new ArrayList<BlockModelImpl>();
    private ArrayList<PatchDefinition> blkPatch = new ArrayList<PatchDefinition>();
    private LinkedHashMap<String, PatchDefinition> blkPatchMap = new LinkedHashMap<String, PatchDefinition>();
    private PatchDefinitionFactory pdf;
    
    public ModModelDefinitionImpl(ModTextureDefinitionImpl txtDef) {
        this.txtDef = txtDef;
        this.pdf = HDBlockModels.getPatchDefinitionFactory();
    }
    
    @Override
    public String getModID() {
        return txtDef.getModID();
    }

    @Override
    public String getModVersion() {
        return txtDef.getModVersion();
    }

    @Override
    public ModTextureDefinition getTextureDefinition() {
        return txtDef;
    }

    @Override
    public boolean publishDefinition() {
        published = true;
        return true;
    }

    @Override
    public VolumetricBlockModel addVolumetricModel(int blockid, int scale) {
        VolumetricBlockModelImpl mod = new VolumetricBlockModelImpl(blockid, this, scale);
        blkModel.add(mod);
        return null;
    }
    @Override
    public VolumetricBlockModel addVolumetricModel(String blockname, int scale) {
        VolumetricBlockModelImpl mod = new VolumetricBlockModelImpl(blockname, this, scale);
        blkModel.add(mod);
        return null;
    }

    @Override
    public StairBlockModel addStairModel(int blockid) {
        StairBlockModelImpl mod = new StairBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public StairBlockModel addStairModel(String blockname) {
        StairBlockModelImpl mod = new StairBlockModelImpl(blockname, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public WallFenceBlockModel addWallFenceModel(int blockid, FenceType type) {
        WallFenceBlockModelImpl mod = new WallFenceBlockModelImpl(blockid, this, type);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public WallFenceBlockModel addWallFenceModel(String blockname, FenceType type) {
        WallFenceBlockModelImpl mod = new WallFenceBlockModelImpl(blockname, this, type);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public CuboidBlockModel addCuboidModel(int blockid) {
        CuboidBlockModelImpl mod = new CuboidBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public CuboidBlockModel addCuboidModel(String blockname) {
        CuboidBlockModelImpl mod = new CuboidBlockModelImpl(blockname, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public PaneBlockModel addPaneModel(int blockid) {
        PaneBlockModelImpl mod = new PaneBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public PaneBlockModel addPaneModel(String blockname) {
        PaneBlockModelImpl mod = new PaneBlockModelImpl(blockname, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public PlantBlockModel addPlantModel(int blockid) {
        PlantBlockModelImpl mod = new PlantBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public PlantBlockModel addPlantModel(String blockname) {
        PlantBlockModelImpl mod = new PlantBlockModelImpl(blockname, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public BoxBlockModel addBoxModel(int blockid) {
        BoxBlockModelImpl mod = new BoxBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public BoxBlockModel addBoxModel(String blockname) {
        BoxBlockModelImpl mod = new BoxBlockModelImpl(blockname, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public DoorBlockModel addDoorModel(int blockid) {
        DoorBlockModelImpl mod = new DoorBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public DoorBlockModel addDoorModel(String blockname) {
        DoorBlockModelImpl mod = new DoorBlockModelImpl(blockname, this);
        blkModel.add(mod);
        return mod;
    }
    
    @Override
    public PatchBlockModel addPatchModel(int blockid) {
        PatchBlockModelImpl mod = new PatchBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public PatchBlockModel addPatchModel(String blockname) {
        PatchBlockModelImpl mod = new PatchBlockModelImpl(blockname, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public PatchBlockModel addRotatedPatchModel(int blockid,
        PatchBlockModel model, int xrot, int yrot, int zrot) {
        PatchBlockModelImpl mod = new PatchBlockModelImpl(blockid, this, model, xrot, yrot, zrot);
        blkModel.add(mod);
        return mod;
    }
    @Override
    public PatchBlockModel addRotatedPatchModel(String blockname,
        PatchBlockModel model, int xrot, int yrot, int zrot) {
        PatchBlockModelImpl mod = new PatchBlockModelImpl(blockname, this, model, xrot, yrot, zrot);
        blkModel.add(mod);
        return mod;
    }

    public String getPatchID(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vminatumax, double vmax, double vmaxatumax, SideVisible sidevis) {
        PatchDefinition pd = pdf.getPatch(x0, y0, z0, xu, yu, zu, xv, yv, zv, umin, umax, vmin, vminatumax, vmax, vmaxatumax, sidevis, 0);
        if (pd == null)
            return null;    // Invalid patch
        for (int i = 0; i < blkPatch.size(); i++) {
            if (blkPatch.get(i) == pd) { 
                return "patch" + i; 
            }
        }
        blkPatch.add(pd);
        String id = "patch" + (blkPatch.size() - 1);
        blkPatchMap.put(id, pd);
        return id;
    }

    public String getRotatedPatchID(String patchid, int xrot, int yrot, int zrot) {
        PatchDefinition pd = blkPatchMap.get(patchid);
        if (pd == null) {
            return null;
        }
        PatchDefinition newpd = (PatchDefinition) pdf.getRotatedPatch(pd, xrot, yrot, zrot, 0);
        if (newpd != null) {
            for (int i = 0; i < blkPatch.size(); i++) {
                if (blkPatch.get(i) == newpd) { 
                    return "patch" + i; 
                }
            }
            blkPatch.add(newpd);
            String id = "patch" + (blkPatch.size() - 1);
            blkPatchMap.put(id, newpd);
            return id;
        }
        return null;
    }


    public boolean isPublished() {
        return published;
    }

    public void writeToFile(File destdir) throws IOException {
        if (blkModel.isEmpty()) {
            return;
        }
        File f = new File(destdir, this.txtDef.getModID() + "-models.txt");
        FileWriter fw = null;
        try {
            fw = new FileWriter(f);
            // Write modname line
            String s = "modname:" + this.txtDef.getModID();
            fw.write(s + "\n\n");
            // Loop through patch definitions
            for (int i = 0; i < blkPatch.size(); i++) {
                PatchDefinition pd = blkPatch.get(i);
                if (pd == null) continue;
                String line = String.format(Locale.US, "patch:id=patch%d,Ox=%f,Oy=%f,Oz=%f,Ux=%f,Uy=%f,Uz=%f,Vx=%f,Vy=%f,Vz=%f,Umin=%f,Umax=%f,Vmin=%f,Vmax=%f,VmaxAtUMax=%f,VminAtUMax=%f",
                        i, pd.x0, pd.y0, pd.z0, pd.xu, pd.yu, pd.zu, pd.xv, pd.yv, pd.zv, pd.umin, pd.umax, pd.vmin, pd.vmax, pd.vmaxatumax, pd.vminatumax);
                switch (pd.sidevis) {
                    case BOTTOM:
                        line += ",visibility=bottom";
                        break;
                    case TOP:
                        line += ",visibility=top";
                        break;
                    case FLIP:
                        line += ",visibility=flip";
                        break;
                    case BOTH:
                        break;
                }
                if (line != null) {
                    fw.write(line + "\n");
                }
            }
            // Loop through block texture records
            for (BlockModelImpl btr : blkModel) {
                String line = btr.getLine();
                if (line != null) {
                    fw.write(line + "\n");
                }
            }
        } finally {
            if (fw != null) {
                fw.close(); 
            }
        }        
    }
}
