package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.VolumetricBlockModel;

public class VolumetricBlockModelImpl extends BlockModelImpl implements VolumetricBlockModel {
    private boolean[][][] grid;
    
    public VolumetricBlockModelImpl(int blkid, ModModelDefinitionImpl mdf, int scale) {
        super(blkid, mdf);
        grid = new boolean[scale][][];
        for (int i = 0; i < scale; i++) {
            grid[i] = new boolean[scale][];
            for (int j = 0; j < scale; j++) {
                grid[i][j] = new boolean[scale];
            }
        }
    }
    public VolumetricBlockModelImpl(String blkname, ModModelDefinitionImpl mdf, int scale) {
        super(blkname, mdf);
        grid = new boolean[scale][][];
        for (int i = 0; i < scale; i++) {
            grid[i] = new boolean[scale][];
            for (int j = 0; j < scale; j++) {
                grid[i][j] = new boolean[scale];
            }
        }
    }
    @Override
    public void setSubBlockToFilled(int x, int y, int z) {
        if ((x >= 0) && (x < grid.length) && (y >= 0) && (y < grid.length) && (z >= 0) && (z < grid.length)) {
            grid[x][y][z] = true;
        }
    }

    @Override
    public void setSubBlockToEmpty(int x, int y, int z) {
        if ((x >= 0) && (x < grid.length) && (y >= 0) && (y < grid.length) && (z >= 0) && (z < grid.length)) {
            grid[x][y][z] = false;
        }
    }

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;
        StringBuilder line = new StringBuilder(String.format("block:%s,scale=%d\n", ids, grid.length));
        for (int y = 0; y < grid.length; y++) {
            StringBuilder l = new StringBuilder("layer: " + y + "\n");
            boolean empty = true;
            for (int z = 0; z < grid.length; z++) {
                for (boolean[][] booleans : grid) {
                    if (booleans[y][z]) {
                        empty = false;
                        l.append('*');
                    } else {
                        l.append('-');
                    }
                }
                l.append("\n");
            }
            if (!empty) {
                line.append(l);
            }
        }
        return line.toString();
    }
}
