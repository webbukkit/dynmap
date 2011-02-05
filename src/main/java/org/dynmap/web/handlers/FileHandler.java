package org.dynmap.web.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;

public abstract class FileHandler implements HttpHandler {
    private byte[] readBuffer = new byte[40960];

    private static Map<String, String> mimes = new HashMap<String, String>();
    static {
        mimes.put(".html", "text/html");
        mimes.put(".htm", "text/html");
        mimes.put(".js", "text/javascript");
        mimes.put(".png", "image/png");
        mimes.put(".css", "text/css");
        mimes.put(".txt", "text/plain");
    }

    public static final String getMimeTypeFromExtension(String extension) {
        String m = mimes.get(extension);
        if (m != null)
            return m;
        return "application/octet-steam";
    }
    
    protected abstract InputStream getFileInput(String path);
    
    protected String getExtension(String path) {
        int dotindex = path.lastIndexOf('.');
        if (dotindex > 0)
            return path.substring(dotindex);
        return null;
    }
    
    protected final String formatPath(String path) {
        int qmark = path.indexOf('?');
        if (qmark >= 0)
            path = path.substring(0, qmark);

        if (path.startsWith("/") || path.startsWith("."))
            return null;
        if (path.length() == 0)
            path = getDefaultFilename(path);
        return path;
    }
    
    protected String getDefaultFilename(String path) {
        return path + "index.html";
    }
    
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws IOException {
        path = formatPath(path);
        InputStream fileInput = getFileInput(path);
        if (fileInput == null) {
            response.statusCode = 404;
            response.statusMessage = "Not found";
            return;
        }
        
        String extension = getExtension(path);

        response.fields.put("Content-Type", getMimeTypeFromExtension(extension));
        response.fields.put("Connection", "close");
        OutputStream out = response.getBody();
        try {
            int readBytes;
            while ((readBytes = fileInput.read(readBuffer)) > 0) {
                out.write(readBuffer, 0, readBytes);
            }
        } catch (IOException e) {
            fileInput.close();
            throw e;
        }
        fileInput.close();
    }
}
