package org.dynmap.web.handlers;

import java.io.BufferedOutputStream;
import java.util.Date;
import java.util.Map;

import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.dynmap.web.Json;

public class ClientConfigurationHandler implements HttpHandler {
    private Map<?, ?> configuration;
    public ClientConfigurationHandler(Map<?, ?> configuration) {
        this.configuration = configuration;
    }
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {
        String s = Json.stringifyJson(configuration);

        byte[] bytes = s.getBytes();
        String dateStr = new Date().toString();
        
        response.fields.put("Date", dateStr);
        response.fields.put("Content-Type", "text/plain");
        response.fields.put("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        response.fields.put("Last-modified", dateStr);
        response.fields.put("Content-Length", Integer.toString(bytes.length));
        BufferedOutputStream out = new BufferedOutputStream(response.getBody());
        out.write(s.getBytes());
        out.flush();
    }
}
