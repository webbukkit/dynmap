package org.dynmap.modsupport.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import org.dynmap.modsupport.CuboidBlockModel;

public class CuboidBlockModelImpl extends BlockModelImpl implements CuboidBlockModel {
    private static class Cuboid {
        double xmin, ymin, zmin;
        double xmax, ymax, zmax;
        int[] textureidx;
    }
    private static class Crossed {
        double xmin, ymin, zmin;
        double xmax, ymax, zmax;
        int textureidx;
    }
    private ArrayList<Cuboid> cuboids = new ArrayList<Cuboid>();
    private ArrayList<Crossed> crosseds = new ArrayList<Crossed>();
    
    @Deprecated
    public CuboidBlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        super(blkid, mdf);
    }
    public CuboidBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
    }

    @Override
    public void addCuboid(double xmin, double ymin, double zmin, double xmax,
            double ymax, double zmax, int[] patchIndices) {
        Cuboid c = new Cuboid();
        c.xmin = xmin;
        c.xmax = xmax;
        c.ymin = ymin;
        c.ymax = ymax;
        c.zmin = zmin;
        c.zmax = zmax;
        if (patchIndices != null) {
            c.textureidx = Arrays.copyOf(patchIndices, 6);
        }
        else {
            c.textureidx = new int[] { 0, 1, 2, 3, 4, 5 };
        }
        cuboids.add(c);
    }

    @Override
    public void addCrossedPatches(double xmin, double ymin, double zmin,
            double xmax, double ymax, double zmax, int patchIndex) {
        Crossed c = new Crossed();
        c.xmin = xmin;
        c.xmax = xmax;
        c.ymin = ymin;
        c.ymax = ymax;
        c.zmin = zmin;
        c.zmax = zmax;
        c.textureidx = patchIndex;
        crosseds.add(c);
    }
    
    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;
        String line = String.format(Locale.US, "customblock:%s,class=org.dynmap.hdmap.renderer.CuboidRenderer", ids);
        for (int i = 0; i < cuboids.size(); i++) {
            Cuboid c = cuboids.get(i);
            // Fix order : CuboidRenderer is (bottom,top,xmin,xmax,zmin,zmax)
            line += String.format(Locale.US, ",cuboid%d=%f:%f:%f/%f:%f:%f/%d:%d:%d:%d:%d:%d", i, c.xmin, c.ymin, c.zmin, c.xmax, c.ymax, c.zmax,
                    c.textureidx[0], c.textureidx[1], c.textureidx[4], c.textureidx[5], c.textureidx[2], c.textureidx[3]);
        }
        for (int i = 0; i < crosseds.size(); i++) {
            Crossed c = crosseds.get(i);
            line += String.format(Locale.US, ",cross%d=%f:%f:%f/%f:%f:%f/%d", i, c.xmin, c.ymin, c.zmin, c.xmax, c.ymax, c.zmax,
                    c.textureidx);
        }
        return line;
    }

}
