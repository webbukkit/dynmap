package org.dynmap.web;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    private HttpServerConnection connection;
    public String version = "1.1";
    public HttpStatus status = null;
    public Map<String, String> fields = new HashMap<String, String>();

    private OutputStream body;
    public OutputStream getBody() throws IOException {
        if (body != null) {
            connection.writeResponseHeader(this);
            OutputStream b = body;
            body = null;
            return b;
        }
        return null;
    }

    public HttpResponse(HttpServerConnection connection, OutputStream body) {
        this.connection = connection;
        this.body = body;
    }
}
