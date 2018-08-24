package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.ShulkerTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class ShulkerTextureFileImpl extends TextureFileImpl implements ShulkerTextureFile {

    public ShulkerTextureFileImpl(String id, String filename) {
        super(id, filename, TextureFileType.SHULKER, 6, 1);
    }
    
    public String getLine() {
        String s = super.getLine();
        s += ",format=SHULKER";
        return s;
    }

}
