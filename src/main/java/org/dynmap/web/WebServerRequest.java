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

public class WebServerRequest extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private Socket socket;
    private WebServer server;

    public WebServerRequest(Socket socket, WebServer server) {
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
        sb.append("\n");
        for (Entry<String, String> field : response.fields.entrySet()) {
            sb.append(field.getKey());
            sb.append(": ");
            sb.append(field.getValue());
            sb.append("\n");
        }
        sb.append("\n");
        o.write(sb.toString().getBytes());
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
            HttpResponse response = null;
            for (Entry<String, HttpHandler> entry : server.handlers.entrySet()) {
                String key = entry.getKey();
                boolean directoryHandler = key.endsWith("/");
                if (directoryHandler && request.path.startsWith(entry.getKey()) || !directoryHandler && request.path.equals(entry.getKey())) {
                    String path = request.path.substring(entry.getKey().length());

                    response = new HttpResponse(socket.getOutputStream());
                    entry.getValue().handle(path, request, response);
                    break;
                }
            }

            if (response != null) {
                if (response.fields.get("Content-Length") == null) {
                    response.fields.put("Content-Length", "0");
                    OutputStream out = response.getBody();
                    if (out != null) {
                        out.close();
                    }
                }

                String connection = response.fields.get("Connection");
                if (connection == null || connection.equals("close")) {
                    socket.close();
                    return;
                }
            } else {
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
