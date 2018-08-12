package org.dynmap.modsupport.impl;

import java.util.ArrayList;

import org.dynmap.modsupport.PatchBlockModel;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

public class PatchBlockModelImpl extends BlockModelImpl implements PatchBlockModel {
    private ArrayList<String> patches = new ArrayList<String>();
    
    public PatchBlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        super(blkid, mdf);
    }
    public PatchBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
    }

    public PatchBlockModelImpl(int blkid, ModModelDefinitionImpl mdf, PatchBlockModel mod, int xrot, int yrot, int zrot) {
        super(blkid, mdf);
        PatchBlockModelImpl m = (PatchBlockModelImpl) mod;
        for (String pid : m.patches) {
            String rotpid = mdf.getRotatedPatchID(pid, xrot, yrot, zrot);
            if (rotpid != null) {
                patches.add(rotpid);
            }
        }
    }
    public PatchBlockModelImpl(String blkname, ModModelDefinitionImpl mdf, PatchBlockModel mod, int xrot, int yrot, int zrot) {
        super(blkname, mdf);
        PatchBlockModelImpl m = (PatchBlockModelImpl) mod;
        for (String pid : m.patches) {
            String rotpid = mdf.getRotatedPatchID(pid, xrot, yrot, zrot);
            if (rotpid != null) {
                patches.add(rotpid);
            }
        }
    }

    @Override
    public String addPatch(double x0, double y0, double z0, double xu, double yu,
            double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, double uplusvmax,
            SideVisible sidevis) {
        String pi = mdf.getPatchID(x0, y0, z0, xu, yu, zu, xv, yv, zv, umin, umax, vmin, vmax, uplusvmax, sidevis);
        patches.add(pi);
        return pi;
    }

    @Override
    public String addPatch(double x0, double y0, double z0, double xu, double yu,
            double zu, double xv, double yv, double zv, SideVisible sidevis) {
        return addPatch(x0, y0, z0, xu, yu, zu, xv, yv, zv, 0.0, 1.0, 0.0, 1.0, 100.0, sidevis);
    }

    @Override
    public String addPatch(double x0, double y0, double z0, double xu, double yu,
            double zu, double xv, double yv, double zv) {
        return addPatch(x0, y0, z0, xu, yu, zu, xv, yv, zv, 0.0, 1.0, 0.0, 1.0, 100.0, SideVisible.BOTH);
    }

    @Override
    public String addRotatedPatch(String patchid, int xrot, int yrot, int zrot) {
        String pi = mdf.getRotatedPatchID(patchid, xrot, yrot, zrot);
        patches.add(pi);
        return pi;
    }

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;
        String line = String.format("patchblock:%s", ids);
        for (int i = 0; i < patches.size(); i++) {
            line += ",patch" + i + "=" + patches.get(i);
        }
        return line;
    }
}
