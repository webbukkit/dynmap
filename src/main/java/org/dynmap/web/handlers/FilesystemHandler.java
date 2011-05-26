package org.dynmap.web.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.dynmap.web.HttpField;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;


public class FilesystemHandler extends FileHandler {
    private File root;
    public FilesystemHandler(File root) {
        if (!root.isDirectory())
            throw new IllegalArgumentException();
        this.root = root;
    }
    @Override
    protected InputStream getFileInput(String path, HttpRequest request, HttpResponse response) {
        File file = new File(root, path);
        if (file.getAbsolutePath().startsWith(root.getAbsolutePath()) && file.isFile()) {
            FileInputStream result;
            try {
                result = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                return null;
            }
            response.fields.put(HttpField.ContentLength, Long.toString(file.length()));
            return result;
        }
        return null;
    }
}
