package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.BiomeTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class BiomeTextureFileImpl extends TextureFileImpl implements BiomeTextureFile {

    public BiomeTextureFileImpl(String id, String filename) {
        super(id, filename, TextureFileType.BIOME, 1, 1);
    }
    public String getLine() {
        String s = super.getLine();
        s += ",format=BIOME";
        return s;
    }
}
