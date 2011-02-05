package org.dynmap.web;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
        
        handlers.put("/", new FilesystemHandler(mgr.webDirectory));
        handlers.put("/tiles/", new FilesystemHandler(mgr.webDirectory));
        handlers.put("/up/", new ClientUpdateHandler());
        handlers.put("/up/configuration", new ClientConfigurationHandler());
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
        sb.append("HTTP/");
        sb.append(response.version);
        sb.append(" ");
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
                log.info("No handler found");
                socket.close();
                return;
            }
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

    
    public class ClientConfigurationHandler implements HttpHandler {
        @Override
        public void handle(String path, HttpRequest request, HttpResponse response) throws IOException {
            String s = stringifyJson(configuration.getProperty("web"));

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

    public class ClientUpdateHandler implements HttpHandler {
        @Override
        public void handle(String path, HttpRequest request, HttpResponse response) throws IOException {
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
            response.fields.put("Date", dateStr);
            response.fields.put("Content-Type", "text/plain");
            response.fields.put("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
            response.fields.put("Last-modified", dateStr);
            response.fields.put("Content-Length", Integer.toString(bytes.length));
            BufferedOutputStream out = new BufferedOutputStream(response.getBody());
            out.write(bytes);
            out.flush();
        }
    }

    public class JarFileHandler extends FileHandler {
        private String root;
        public JarFileHandler(String root) {
            if (root.endsWith("/")) root = root.substring(0, root.length()-1);
            this.root = root;
        }
        @Override
        protected InputStream getFileInput(String path) {
            return this.getClass().getResourceAsStream(root + "/" + path);
        }
    }
    
    public class FilesystemHandler extends FileHandler {
        private File root;
        public FilesystemHandler(File root) {
            if (!root.isDirectory())
                throw new IllegalArgumentException();
            this.root = root;
        }
        @Override
        protected InputStream getFileInput(String path) {
            File file = new File(root, path);
            if (file.getAbsolutePath().startsWith(root.getAbsolutePath()) && file.isFile()) {
                try {
                    return new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    return null;
                }
            }
            return null;
        }
    }
}
