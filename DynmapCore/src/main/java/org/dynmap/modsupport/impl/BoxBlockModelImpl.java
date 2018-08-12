package org.dynmap.modsupport.impl;

import java.util.Locale;

import org.dynmap.modsupport.BoxBlockModel;

public class BoxBlockModelImpl extends BlockModelImpl implements BoxBlockModel {
    private double xmin = 0.0;
    private double xmax = 1.0;
    private double ymin = 0.0;
    private double ymax = 1.0;
    private double zmin = 0.0;
    private double zmax = 1.0;
    
    public BoxBlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        super(blkid, mdf);
    }
    public BoxBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
    }

    @Override
    public void setXRange(double xmin, double xmax) {
        this.xmin = xmin;
        this.xmax = xmax;
    }

    @Override
    public void setYRange(double ymin, double ymax) {
        this.ymin = ymin;
        this.ymax = ymax;
    }

    @Override
    public void setZRange(double zmin, double zmax) {
        this.zmin = zmin;
        this.zmax = zmax;
    }

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;
        return String.format(Locale.US, "boxblock:%s,xmin=%f,xmax=%f,ymin=%f,ymax=%f,zmin=%f,zmax=%f",
                ids, xmin, xmax, ymin, ymax, zmin, zmax);
    }

}
