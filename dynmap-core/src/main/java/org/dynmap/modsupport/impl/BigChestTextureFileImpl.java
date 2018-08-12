package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.BigChestTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class BigChestTextureFileImpl extends TextureFileImpl implements BigChestTextureFile {

    public BigChestTextureFileImpl(String id, String filename) {
        super(id, filename, TextureFileType.BIGCHEST, 10, 1);
    }
    
    public String getLine() {
        String s = super.getLine();
        s += ",format=BIGCHEST";
        return s;
    }
}
