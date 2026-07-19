package org.dynmap.utils;

public class RectangleVisibilityLimit implements VisibilityLimit {
    public int x_min, x_max, z_min, z_max;


    public RectangleVisibilityLimit(int x0, int z0, int x1, int z1) {
        if (x1 >= x0) {
            x_min = x0;
            x_max = x1;
        }
        else {
            x_min = x1;
            x_max = x0;
        }
        if (z1 >= z0) {
            z_min = z0;
            z_max = z1;
        }
        else {
            z_min = z1;
            z_max = z0;
        }
    }

    @Override
    public boolean doIntersectChunk(int chunk_x, int chunk_z) {
        return ((chunk_x * 16 + 15) >= x_min) && ((chunk_x * 16) <= x_max) && ((chunk_z * 16 + 15) >= z_min) && ((chunk_z * 16) <= z_max);
    }

    @Override
    public int xCenter() {
        return (x_min + x_max) / 2;
    }

    @Override
    public int zCenter() {
        return (z_min + z_max) / 2;
    }

}
