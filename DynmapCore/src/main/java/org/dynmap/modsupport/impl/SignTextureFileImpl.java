package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.SignTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class SignTextureFileImpl extends TextureFileImpl implements SignTextureFile {

    public SignTextureFileImpl(String id, String filename) {
        super(id, filename, TextureFileType.SIGN, 10, 1);
    }
    public String getLine() {
        String s = super.getLine();
        s += ",format=SIGN";
        return s;
    }
}
