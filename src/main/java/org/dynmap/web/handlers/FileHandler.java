package org.dynmap.web.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Logger;

import org.dynmap.web.HttpField;
import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.dynmap.web.HttpStatus;

public abstract class FileHandler implements HttpHandler {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private LinkedList<byte[]> bufferpool = new LinkedList<byte[]>();
    private Object lock = new Object();
    private static final int MAX_FREE_IN_POOL = 2;

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

    protected abstract InputStream getFileInput(String path, HttpRequest request, HttpResponse response);
    
    protected void closeFileInput(String path, InputStream in) throws IOException {
        in.close();
    }

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

    private byte[] allocateReadBuffer() {
        byte[] buf;
        synchronized(lock) {
            buf = bufferpool.poll();
        }
        if(buf == null) {
            buf = new byte[40960];
        }
        return buf;
    }
    
    private void freeReadBuffer(byte[] buf) {
        synchronized(lock) {
            if(bufferpool.size() < MAX_FREE_IN_POOL)
                bufferpool.push(buf);
        }
    }
    
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {
        InputStream fileInput = null;
        try {
            path = formatPath(path);
            fileInput = getFileInput(path, request, response);
            if (fileInput == null) {
                response.status = HttpStatus.NotFound;
                return;
            }

            String extension = getExtension(path);
            String mimeType = getMimeTypeFromExtension(extension);

            response.fields.put(HttpField.ContentType, mimeType);
            response.status = HttpStatus.OK;
            OutputStream out = response.getBody();
            byte[] readBuffer = allocateReadBuffer();            
            try {
                int readBytes;
                while ((readBytes = fileInput.read(readBuffer)) > 0) {
                    out.write(readBuffer, 0, readBytes);
                }
            } finally {
                freeReadBuffer(readBuffer);
            }
        } finally {
            if (fileInput != null) {
                try { closeFileInput(path, fileInput); fileInput = null; } catch (IOException ex) { }
            }
        }
    }
}
