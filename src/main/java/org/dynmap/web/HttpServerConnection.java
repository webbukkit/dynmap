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

import org.dynmap.debug.Debug;

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
        String statusLine = r.readLine();
        if (statusLine == null)
            return false;
        Matcher m = requestHeaderLine.matcher(statusLine);
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
            socket.setSoTimeout(5000);
            while (true) {
                HttpRequest request = new HttpRequest();
                InputStream in = socket.getInputStream();
                if (!readRequestHeader(in, request)) {
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

                OutputStream out = socket.getOutputStream();
                HttpResponse response = new HttpResponse(out);

                try {
                    handler.handle(relativePath, request, response);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    log.log(Level.SEVERE, "HttpHandler '" + handler + "' has thown an exception", e);
                    if (socket != null) {
                        out.flush();
                        socket.close();
                    }
                    return;
                }

                String connection = response.fields.get("Connection");
                String contentLength = response.fields.get("Content-Length");
                if (contentLength == null && connection == null) {
                    response.fields.put("Content-Length", "0");
                    OutputStream responseBody = response.getBody();

                    // The HttpHandler has already send the headers and written to the body without setting the Content-Length.
                    if (responseBody == null) {
                        Debug.debug("Response was given without Content-Length by '" + handler + "' for path '" + request.path + "'.");
                        out.flush();
                        socket.close();
                        return;
                    }
                }

                if (connection != null && connection.equals("close")) {
                    out.flush();
                    socket.close();
                    return;
                }

                out.flush();
            }
        } catch (IOException e) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                }
            }
            return;
        } catch (Exception e) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                }
            }
            log.log(Level.SEVERE, "Exception while handling request: ", e);
            e.printStackTrace();
            return;
        }
    }
}
