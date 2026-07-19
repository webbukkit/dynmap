package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.SkinTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class SkinTextureFileImpl extends TextureFileImpl implements SkinTextureFile {

    public SkinTextureFileImpl(String id, String filename) {
        super(id, filename, TextureFileType.SKIN, 6, 1);
    }
    public String getLine() {
        String s = super.getLine();
        s += ",format=SKIN";
        return s;
    }
}
