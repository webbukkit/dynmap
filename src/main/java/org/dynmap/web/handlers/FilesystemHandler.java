package org.dynmap.web.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;


public class FilesystemHandler extends FileHandler {
    private File root;
    public FilesystemHandler(File root) {
        if (!root.isDirectory())
            throw new IllegalArgumentException();
        this.root = root;
    }
    @Override
    protected InputStream getFileInput(String path) {
        File file = new File(root, path);
        if (file.getAbsolutePath().startsWith(root.getAbsolutePath()) && file.isFile()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
        return null;
    }
}