package org.dynmap.web;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    public String version = "1.0";
    public int statusCode = 200;
    public String statusMessage = "OK";
    public Map<String, String> fields = new HashMap<String, String>();
    
    private OutputStream body;
    public OutputStream getBody() throws IOException {
        if (body != null) {
            WebServerRequest.writeResponseHeader(body, this);
            OutputStream b = body;
            body = null;
            return b;
        }
        return null;
    }
    
    public HttpResponse(OutputStream body) {
        this.body = body;
    }
}
