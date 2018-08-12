package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.ChestTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class ChestTextureFileImpl extends TextureFileImpl implements ChestTextureFile {

    public ChestTextureFileImpl(String id, String filename) {
        super(id, filename, TextureFileType.CHEST, 6, 1);
    }
    
    public String getLine() {
        String s = super.getLine();
        s += ",format=CHEST";
        return s;
    }

}
