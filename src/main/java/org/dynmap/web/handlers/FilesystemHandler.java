package org.dynmap.web.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.dynmap.Log;
import org.dynmap.utils.FileLockManager;
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
        FileLockManager.getReadLock(file);
        try {
            if (file.getCanonicalPath().startsWith(root.getAbsolutePath()) && file.isFile()) {
                FileInputStream result;
                try {
                    result = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    FileLockManager.releaseReadLock(file);
                    return null;
                }
                response.fields.put(HttpField.ContentLength, Long.toString(file.length()));
                return result;
            }
        } catch(IOException ex) {
            Log.severe("Unable to get canoical path of requested file.", ex);
        }
        FileLockManager.releaseReadLock(file);
        return null;
    }
    protected void closeFileInput(String path, InputStream in) throws IOException {
        super.closeFileInput(path, in);
        File file = new File(root, path);
        FileLockManager.releaseReadLock(file);
    }

}
