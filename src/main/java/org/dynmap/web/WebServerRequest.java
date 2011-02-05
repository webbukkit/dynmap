package org.dynmap.web;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.ChatQueue;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.TileUpdate;
import org.dynmap.debug.Debugger;

public class WebServerRequest extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private Debugger debugger;
    private Socket socket;
    private MapManager mgr;
    private World world;
    private PlayerList playerList;
    private ConfigurationNode configuration;
    
    public SortedMap<String, HttpHandler> handlers = new TreeMap<String, HttpHandler>(Collections.reverseOrder());

    public WebServerRequest(Socket socket, MapManager mgr, World world, PlayerList playerList, ConfigurationNode configuration, Debugger debugger) {
        this.debugger = debugger;
        this.socket = socket;
        this.mgr = mgr;
        this.world = world;
        this.playerList = playerList;
        this.configuration = configuration;
        
        handlers.put("/", new HttpHandler() {
            @Override
            public void handle(String path, HttpRequest request, HttpResponse response) throws IOException {
                response.fields.put("Content-Type", "text/plain");
                BufferedOutputStream s = new BufferedOutputStream(response.getBody());
                s.write("Hallo".getBytes());
                s.flush();
                s.close();
            }
        });
        handlers.put("/test/", new HttpHandler() {
            @Override
            public void handle(String path, HttpRequest request, HttpResponse response) throws IOException {
                response.fields.put("Content-Type", "text/plain");
                BufferedOutputStream s = new BufferedOutputStream(response.getBody());
                s.write("test".getBytes());
                s.flush();
                s.close();
            }
        });
    }

    private static void writeHttpHeader(BufferedOutputStream out, int statusCode, String statusText) throws IOException {
        out.write("HTTP/1.0 ".getBytes());
        out.write(Integer.toString(statusCode).getBytes());
        out.write((" " + statusText + "\r\n").getBytes());
    }

    private static void writeHeaderField(BufferedOutputStream out, String name, String value) throws IOException {
        out.write(name.getBytes());
        out.write((int) ':');
        out.write((int) ' ');
        out.write(value.getBytes());
        out.write(13);
        out.write(10);
    }

    private static void writeEndOfHeaders(BufferedOutputStream out) throws IOException {
        out.write(13);
        out.write(10);
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
        while((line = r.readLine()) != null) {
            log.info("Header line: " + line);
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
        sb.append(response.statusCode);
        sb.append(" ");
        sb.append(response.statusMessage);
        sb.append("\n");
        for(Entry<String,String> field : response.fields.entrySet()) {
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
            log.info("Reading request...");
            if (!readRequestHeader(socket.getInputStream(), request)) {
                log.info("Invalid request header, aborting...");
                socket.close();
                return;
            }

            // TODO: Optimize HttpHandler-finding by using a real path-aware tree.
            HttpResponse response = null;
            for(Entry<String, HttpHandler> entry : handlers.entrySet()) {
                String key = entry.getKey();
                boolean directoryHandler = key.endsWith("/");
                if (directoryHandler && request.path.startsWith(entry.getKey()) ||
                        !directoryHandler && request.path.equals(entry.getKey())) {
                    String path = request.path.substring(entry.getKey().length());
                    
                    response = new HttpResponse(socket.getOutputStream());
                    entry.getValue().handle(path, request, response);
                    break;
                }
            }
            
            if (response != null) {
                String connection = response.fields.get("Connection");
                if (connection != null && connection.equals("close")) {
                    socket.close();
                    return;
                }
            } else {
                log.info("No handler found");
                socket.close();
                return;
            }
            /*debugger.debug("request: " + path);
            if (path.equals("/up/configuration")) {
                handleConfiguration(out);
            } else if (path.startsWith("/up/")) {
                handleUp(out, path.substring(3));
            } else if (path.startsWith("/tiles/")) {
                handleMapToDirectory(out, path.substring(6), mgr.tileDirectory);
            } else if (path.startsWith("/")) {
                handleMapToDirectory(out, path, mgr.webDirectory);
            }*/
        } catch (IOException e) {
            try { socket.close(); } catch(IOException ex) { }
        } catch (Exception e) {
            try { socket.close(); } catch(IOException ex) { }
            debugger.error("Exception on WebRequest-thread: " + e.toString());
            e.printStackTrace();
        }
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
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Object> m = (LinkedHashMap<String, Object>) o;
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (String key : m.keySet()) {
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
            @SuppressWarnings("unchecked")
            ArrayList<Object> l = (ArrayList<Object>) o;
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

    public void handleConfiguration(BufferedOutputStream out) throws IOException {

        String s = stringifyJson(configuration.getProperty("web"));

        byte[] bytes = s.getBytes();
        String dateStr = new Date().toString();
        writeHttpHeader(out, 200, "OK");
        writeHeaderField(out, "Date", dateStr);
        writeHeaderField(out, "Content-Type", "text/plain");
        writeHeaderField(out, "Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        writeHeaderField(out, "Last-modified", dateStr);
        writeHeaderField(out, "Content-Length", Integer.toString(bytes.length));
        writeEndOfHeaders(out);
        out.write(bytes);
    }

    public void handleUp(BufferedOutputStream out, String path) throws IOException {
        int current = (int) (System.currentTimeMillis() / 1000);
        long cutoff = 0;

        if (path.charAt(0) == '/') {
            try {
                cutoff = ((long) Integer.parseInt(path.substring(1))) * 1000;
            } catch (NumberFormatException e) {
            }
        }

        StringBuilder sb = new StringBuilder();
        long relativeTime = world.getTime() % 24000;
        sb.append(current + " " + relativeTime + "\n");

        Player[] players = playerList.getVisiblePlayers();
        for (Player player : players) {
            sb.append("player " + player.getName() + " " + player.getLocation().getX() + " " + player.getLocation().getY() + " " + player.getLocation().getZ() + "\n");
        }

        TileUpdate[] tileUpdates = mgr.staleQueue.getTileUpdates(cutoff);
        for (TileUpdate tu : tileUpdates) {
            sb.append("tile " + tu.tile.getName() + "\n");
        }

        ChatQueue.ChatMessage[] messages = mgr.chatQueue.getChatMessages(cutoff);
        for (ChatQueue.ChatMessage cu : messages) {
            sb.append("chat " + cu.playerName + " " + cu.message + "\n");
        }

        debugger.debug("Sending " + players.length + " players, " + tileUpdates.length + " tile-updates, and " + messages.length + " chats. " + path + ";" + cutoff);

        byte[] bytes = sb.toString().getBytes();

        String dateStr = new Date().toString();
        writeHttpHeader(out, 200, "OK");
        writeHeaderField(out, "Date", dateStr);
        writeHeaderField(out, "Content-Type", "text/plain");
        writeHeaderField(out, "Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        writeHeaderField(out, "Last-modified", dateStr);
        writeHeaderField(out, "Content-Length", Integer.toString(bytes.length));
        writeEndOfHeaders(out);
        out.write(bytes);
    }

    private byte[] readBuffer = new byte[40960];

    public void writeFile(BufferedOutputStream out, String path, InputStream fileInput) throws IOException {
        int dotindex = path.lastIndexOf('.');
        String extension = null;
        if (dotindex > 0)
            extension = path.substring(dotindex);

        writeHttpHeader(out, 200, "OK");
        writeHeaderField(out, "Content-Type", getMimeTypeFromExtension(extension));
        writeHeaderField(out, "Connection", "close");
        writeEndOfHeaders(out);
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

    public String getFilePath(String path) {
        int qmark = path.indexOf('?');
        if (qmark >= 0)
            path = path.substring(0, qmark);
        path = path.substring(1);

        if (path.startsWith("/") || path.startsWith("."))
            return null;
        if (path.length() == 0)
            path = "index.html";
        return path;
    }

    public void handleMapToJar(BufferedOutputStream out, String path) throws IOException {
        path = getFilePath(path);
        if (path != null) {
            InputStream s = this.getClass().getResourceAsStream("/web/" + path);
            if (s != null) {
                writeFile(out, path, s);
                return;
            }
        }
        writeHttpHeader(out, 404, "Not found");
        writeEndOfHeaders(out);
    }

    public void handleMapToDirectory(BufferedOutputStream out, String path, File directory) throws IOException {
        path = getFilePath(path);
        if (path != null) {
            File tileFile = new File(directory, path);

            if (tileFile.getAbsolutePath().startsWith(directory.getAbsolutePath()) && tileFile.isFile()) {
                FileInputStream s = new FileInputStream(tileFile);
                writeFile(out, path, s);
                return;
            }
        }
        writeHttpHeader(out, 404, "Not found");
        writeEndOfHeaders(out);
    }

    private static Map<String, String> mimes = new HashMap<String, String>();
    static {
        mimes.put(".html", "text/html");
        mimes.put(".htm", "text/html");
        mimes.put(".js", "text/javascript");
        mimes.put(".png", "image/png");
        mimes.put(".css", "text/css");
        mimes.put(".txt", "text/plain");
    }

    public static String getMimeTypeFromExtension(String extension) {
        String m = mimes.get(extension);
        if (m != null)
            return m;
        return "application/octet-steam";
    }
}
