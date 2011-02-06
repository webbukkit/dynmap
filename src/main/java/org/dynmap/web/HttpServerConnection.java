package org.dynmap.web;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpServerConnection extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private Socket socket;
    private HttpServer server;

    public HttpServerConnection(Socket socket, HttpServer server) {
        this.socket = socket;
        this.server = server;
    }

    private static Pattern requestHeaderLine = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+HTTP/(.+)$");
    private static Pattern requestHeaderField = Pattern.compile("^([^:]+):\\s*(.+)$");

    private static boolean readRequestHeader(InputStream in, HttpRequest request) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        Matcher m = requestHeaderLine.matcher(r.readLine());
        if (!m.matches())
            return false;
        request.method = m.group(1);
        request.path = m.group(2);
        request.version = m.group(3);

        String line;
        while ((line = r.readLine()) != null) {
            if (line.equals(""))
                break;
            m = requestHeaderField.matcher(line);
            // Warning: unknown lines are ignored.
            if (m.matches()) {
                String fieldName = m.group(1);
                String fieldValue = m.group(2);
                // TODO: Does not support duplicate field-names.
                request.fields.put(fieldName, fieldValue);
            }
        }
        return true;
    }

    public static void writeResponseHeader(OutputStream out, HttpResponse response) throws IOException {
        BufferedOutputStream o = new BufferedOutputStream(out);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/");
        sb.append(response.version);
        sb.append(" ");
        sb.append(response.statusCode);
        sb.append(" ");
        sb.append(response.statusMessage);
        sb.append("\r\n");
        for (Entry<String, String> field : response.fields.entrySet()) {
            sb.append(field.getKey());
            sb.append(": ");
            sb.append(field.getValue());
            sb.append("\r\n");
        }
        sb.append("\r\n");
        o.write(sb.toString().getBytes());
        o.flush();
    }

    public void run() {
        try {
            socket.setSoTimeout(30000);

            HttpRequest request = new HttpRequest();
            if (!readRequestHeader(socket.getInputStream(), request)) {
                socket.close();
                return;
            }

            // TODO: Optimize HttpHandler-finding by using a real path-aware
            // tree.
            HttpHandler handler = null;
            String relativePath = null;
            for (Entry<String, HttpHandler> entry : server.handlers.entrySet()) {
                String key = entry.getKey();
                boolean directoryHandler = key.endsWith("/");
                if (directoryHandler && request.path.startsWith(entry.getKey()) || !directoryHandler && request.path.equals(entry.getKey())) {
                    relativePath = request.path.substring(entry.getKey().length());
                    handler = entry.getValue();
                    break;
                }
            }

            if (handler == null) {
                socket.close();
                return;
            }

            HttpResponse response = new HttpResponse(socket.getOutputStream());

            try {
                handler.handle(relativePath, request, response);
            } catch (Exception e) {
                log.log(Level.SEVERE, "HttpHandler '" + handler + "' has thown an exception", e);
                e.printStackTrace();
                socket.close();
                return;
            }

            if (response.fields.get("Content-Length") == null) {
                response.fields.put("Content-Length", "0");
                /* OutputStream out = */response.getBody();
            }

            String connection = response.fields.get("Connection");
            if (connection != null && connection.equals("close")) {
                socket.close();
                return;
            }
        } catch (IOException e) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                }
            }
        } catch (Exception e) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                }
            }
            log.log(Level.SEVERE, "Exception while handling request: ", e);
            e.printStackTrace();
        }
    }
}
