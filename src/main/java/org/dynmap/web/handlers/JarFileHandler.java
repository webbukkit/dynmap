package org.dynmap.web.handlers;

import java.io.InputStream;

import org.dynmap.web.FileHandler;

public class JarFileHandler extends FileHandler {
    private String root;
    public JarFileHandler(String root) {
        if (root.endsWith("/")) root = root.substring(0, root.length()-1);
        this.root = root;
    }
    @Override
    protected InputStream getFileInput(String path) {
        return this.getClass().getResourceAsStream(root + "/" + path);
    }
}