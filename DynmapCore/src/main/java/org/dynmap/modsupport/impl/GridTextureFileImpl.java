package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.GridTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class GridTextureFileImpl extends TextureFileImpl implements GridTextureFile {

    public GridTextureFileImpl(String id, String filename, int xcount, int ycount) {
        super(id, filename, TextureFileType.GRID, xcount, ycount);
    }
}
