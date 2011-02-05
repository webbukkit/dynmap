package org.dynmap.web.handlers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;

public class ClientConfigurationHandler implements HttpHandler {
    private Map<?, ?> configuration;
    public ClientConfigurationHandler(Map<?, ?> configuration) {
        this.configuration = configuration;
    }
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws IOException {
        String s = stringifyJson(configuration);

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
    
    public String stringifyJson(Object o) {
        if (o == null) {
            return "null";
        } else if (o instanceof Boolean) {
            return ((Boolean) o) ? "true" : "false";
        } else if (o instanceof String) {
            return "\"" + o + "\"";
        } else if (o instanceof Integer || o instanceof Long || o instanceof Float || o instanceof Double) {
            return o.toString();
        } else if (o instanceof LinkedHashMap<?, ?>) {
            LinkedHashMap<?, ?> m = (LinkedHashMap<?, ?>) o;
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Object key : m.keySet()) {
                if (first)
                    first = false;
                else
                    sb.append(",");

                sb.append(stringifyJson(key));
                sb.append(": ");
                sb.append(stringifyJson(m.get(key)));
            }
            sb.append("}");
            return sb.toString();
        } else if (o instanceof ArrayList<?>) {
            ArrayList<?> l = (ArrayList<?>) o;
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (int i = 0; i < l.size(); i++) {
                sb.append(count++ == 0 ? "[" : ",");
                sb.append(stringifyJson(l.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else {
            return "undefined";
        }
    }
}
