package org.dynmap.web.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.dynmap.Log;
import org.dynmap.utils.FileLockManager;
import org.dynmap.web.HttpField;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;


public class FilesystemHandler extends FileHandler {
    private File root;
    private boolean allow_symlinks = false;
    private String root_path;
    public FilesystemHandler(File root, boolean allow_symlinks) {
        if (!root.isDirectory())
            throw new IllegalArgumentException();
        this.root = root;
        this.allow_symlinks = allow_symlinks;
        this.root_path = root.getAbsolutePath();
    }
    @Override
    protected InputStream getFileInput(String path, HttpRequest request, HttpResponse response) {
    	if(path == null) return null;
    	path = getNormalizedPath(path);	/* Resolve out relative stuff - nothing allowed above webroot */
        File file = new File(root, path);
        if(!file.isFile())
        	return null;
        if(!FileLockManager.getReadLock(file, 5000)) {    /* Wait up to 5 seconds for lock */
            Log.severe("Timeout waiting for lock on file " + file.getPath());
            return null;
        }
        FileInputStream result = null;
        try {
        	String fpath;
        	if(allow_symlinks)
        		fpath = file.getAbsolutePath();
        	else
        		fpath = file.getCanonicalPath();
            if (fpath.startsWith(root_path)) {
                try {
                    result = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    return null;
                }
                response.fields.put(HttpField.ContentLength, Long.toString(file.length()));
                return result;
            }
        } catch(IOException ex) {
            Log.severe("Unable to get canoical path of requested file.", ex);
        } finally {
            if(result == null) FileLockManager.releaseReadLock(file);
        }
        return null;
    }
    protected void closeFileInput(String path, InputStream in) throws IOException {
    	path = getNormalizedPath(path);
        try {
            super.closeFileInput(path, in);
        } finally {
            File file = new File(root, path);
            FileLockManager.releaseReadLock(file);
        }
    }
    public static String getNormalizedPath(String p) {
    	p = p.replace('\\', '/');
    	String[] tok = p.split("/");
    	int i, j;
    	for(i = 0, j = 0; i < tok.length; i++) {
    		if((tok[i] == null) || (tok[i].length() == 0) || (tok[i].equals("."))) {
    			tok[i] = null;
    		}
    		else if(tok[i].equals("..")) {
    			if(j > 0) { j--; tok[j] = null;  }
    			tok[i] = null;
    		}
    		else {
    			tok[j] = tok[i];
    			j++;
    		}
    	}
    	String path = "";
    	for(i = 0; i < j; i++) {
    		if(tok[i] != null)
    			path = path + "/" + tok[i];
    	}
    	return path;
    }
}
