package org.dynmap.web;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dynmap.Log;
import org.dynmap.debug.Debug;
import java.net.InetSocketAddress;

public class HttpServerConnection extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private static Pattern requestHeaderLine = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+HTTP/(.+)$");
    private static Pattern requestHeaderField = Pattern.compile("^([^:]+):\\s*(.+)$");

    private Socket socket;
    private HttpServer server;
    private boolean do_shutdown;
    private boolean can_keepalive;

    private PrintStream printOut;
    private StringWriter sw = new StringWriter();
    private Matcher requestHeaderLineMatcher;
    private Matcher requestHeaderFieldMatcher;

    public HttpServerConnection(Socket socket, HttpServer server) {
        this.socket = socket;
        this.server = server;
        do_shutdown = false;
        can_keepalive = false;
    }

    private final static void readLine(InputStream in, StringWriter sw) throws IOException {
        int readc;
        while((readc = in.read()) > 0) {
            char c = (char)readc;
            if (c == '\n')
                break;
            else if (c != '\r')
                sw.append(c);
        }
    }

    private final String readLine(InputStream in) throws IOException {
        readLine(in, sw);
        String r = sw.toString();
        sw.getBuffer().setLength(0);
        return r;
    }

    private final boolean readRequestHeader(InputStream in, HttpRequest request) throws IOException {
        String statusLine = readLine(in);

        if (statusLine == null)
            return false;

        if (requestHeaderLineMatcher == null) {
            requestHeaderLineMatcher = requestHeaderLine.matcher(statusLine);
        } else {
            requestHeaderLineMatcher.reset(statusLine);
        }

        Matcher m = requestHeaderLineMatcher;
        if (!m.matches())
            return false;
        request.method = m.group(1);
        request.path = m.group(2);
        request.version = m.group(3);

        String line;
        while (!(line = readLine(in)).equals("")) {
            if (requestHeaderFieldMatcher == null) {
                requestHeaderFieldMatcher = requestHeaderField.matcher(line);
            } else {
                requestHeaderFieldMatcher.reset(line);
            }

            m = requestHeaderFieldMatcher;
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

    public static final void writeResponseHeader(PrintStream out, HttpResponse response) throws IOException {
        out.append("HTTP/");
        out.append(response.version);
        out.append(" ");
        out.append(String.valueOf(response.status.getCode()));
        out.append(" ");
        out.append(response.status.getText());
        out.append("\r\n");
        for (Entry<String, String> field : response.fields.entrySet()) {
            out.append(field.getKey());
            out.append(": ");
            out.append(field.getValue());
            out.append("\r\n");
        }
        out.append("\r\n");
        out.flush();
    }

    public final void writeResponseHeader(HttpResponse response) throws IOException {
        writeResponseHeader(printOut, response);
    }
    
    public void run() {
        try {
            if (socket == null)
                return;
            socket.setSoTimeout(5000);
            socket.setTcpNoDelay(true);
            InetSocketAddress rmtaddr = (InetSocketAddress)socket.getRemoteSocketAddress(); /* Get remote address */
            InputStream in = socket.getInputStream();
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream(), 40960);

            printOut = new PrintStream(out, false);
            while (true) {
            	/* Check for start of each request - kicks out persistent connections */
                if(server.checkForBannedIp(rmtaddr)) {
                	return;
                }
                
                HttpRequest request = new HttpRequest();
                request.rmtaddr = rmtaddr;
                if (!readRequestHeader(in, request)) {
                    return;
                }

                long bound = -1;
                BoundInputStream boundBody = null;
                {
                    String contentLengthStr = request.fields.get(HttpField.ContentLength);
                    if (contentLengthStr != null) {
                        try {
                            bound = Long.parseLong(contentLengthStr);
                        } catch (NumberFormatException e) {
                        }
                        if (bound >= 0) {
                            request.body = boundBody = new BoundInputStream(in, bound);
                        } else {
                            request.body = in;
                        }
                    }
                }
                boolean iskeepalive = false;
                String keepalive = request.fields.get(HttpField.Connection);
                if((keepalive != null) && (keepalive.toLowerCase().indexOf("keep-alive") >= 0)) {
                    /* See if we're clear to do keepalive */
                    if(!iskeepalive)
                        iskeepalive = server.canKeepAlive(this);
                }

                // TODO: Optimize HttpHandler-finding by using a real path-aware tree.
                HttpHandler handler = null;
                String relativePath = null;
                for (Entry<String, HttpHandler> entry : server.handlers.entrySet()) {
                    String key = entry.getKey();
                    boolean directoryHandler = key.endsWith("/");
                    if (directoryHandler && request.path.startsWith(entry.getKey()) || !directoryHandler && request.path.equals(entry.getKey())) {
                        relativePath = request.path.substring(entry.getKey().length());
                        relativePath = URLDecoder.decode(relativePath,"utf-8");
                        handler = entry.getValue();
                        break;
                    }
                    /* Wildcard handler for non-directory matches */
                    else if(key.endsWith("*") && request.path.startsWith(key.substring(0, key.length()-1))) {                        relativePath = request.path.substring(entry.getKey().length());
                        relativePath = request.path.substring(entry.getKey().length()-1);
                        relativePath = URLDecoder.decode(relativePath,"utf-8");
                        handler = entry.getValue();
                        break;
                    }
                }

                if (handler == null) {
                    return;
                }

                HttpResponse response = new HttpResponse(this, out);

                if(iskeepalive) {
                    response.fields.put(HttpField.Connection, "keep-alive");
                    response.fields.put("Keep-Alive", "timeout=5");
                }
                else {
                    response.fields.put(HttpField.Connection, "close");
                }
                try {
                    handler.handle(relativePath, request, response);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    Log.severe("HttpHandler '" + handler + "' has thown an exception", e);
                    out.flush();
                    return;
                }

                if (bound > 0 && boundBody.skip(bound) < bound) {
                    Debug.debug("Incoming stream was only read partially by handler '" + handler + "'.");
                    //socket.close();
                    //return;
                }

                boolean isKeepalive = iskeepalive && !"close".equals(request.fields.get(HttpField.Connection)) && !"close".equals(response.fields.get(HttpField.Connection));
                String contentLength = response.fields.get("Content-Length");
                if (isKeepalive && contentLength == null) {
                    // A handler has been a bad boy, but we're here to fix it.
                    response.fields.put("Content-Length", "0");
                    OutputStream responseBody = response.getBody();

                    // The HttpHandler has already send the headers and written to the body without setting the Content-Length.
                    if (responseBody == null) {
                        Debug.debug("Response was given without Content-Length by '" + handler + "' for path '" + request.path + "'.");
                        out.flush();
                        return;
                    }
                }

                out.flush();

                if (!isKeepalive) {
                    return;
                }
            }
        } catch (IOException e) {

        } catch (Exception e) {
            if(!do_shutdown) {
                Log.severe("Exception while handling request: ", e);
                e.printStackTrace();
            }
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                }
            }
            server.connectionEnded(this);
        }
    }
    public void shutdownConnection() {
        try {
            do_shutdown = true;
            if(socket != null) {
                socket.close();
            }
            join(); /* Wait for thread to die */
        } catch (IOException iox) {
        } catch (InterruptedException ix) {
        }
    }
}
