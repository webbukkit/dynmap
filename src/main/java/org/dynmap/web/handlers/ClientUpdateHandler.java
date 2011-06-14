package org.dynmap.web.handlers;

import java.io.BufferedOutputStream;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dynmap.ClientUpdateEvent;
import org.dynmap.DynmapPlugin;
import org.dynmap.DynmapWorld;
import org.dynmap.web.HttpField;
import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.dynmap.web.HttpStatus;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.*;

public class ClientUpdateHandler implements HttpHandler {
    private DynmapPlugin plugin;
    
    public ClientUpdateHandler(DynmapPlugin plugin) {
        this.plugin = plugin;
    }

    Pattern updatePathPattern = Pattern.compile("world/([^/]+)/([0-9]*)");
    private static final HttpStatus WorldNotFound = new HttpStatus(HttpStatus.NotFound.getCode(), "World Not Found");
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {

        Matcher match = updatePathPattern.matcher(path);

        if (!match.matches()) {
            response.status = HttpStatus.Forbidden;
            return;
        }

        String worldName = match.group(1);
        String timeKey = match.group(2);

        DynmapWorld dynmapWorld = plugin.mapManager.getWorld(worldName);
        if (dynmapWorld == null || dynmapWorld.world == null) {
            response.status = WorldNotFound;
            return;
        }
        long current = System.currentTimeMillis();
        long since = 0;

        if (path.length() > 0) {
            try {
                since = Long.parseLong(timeKey);
            } catch (NumberFormatException e) {
            }
        }

        JSONObject u = new JSONObject();
        s(u, "timestamp", current);
        plugin.events.trigger("buildclientupdate", new ClientUpdateEvent(since, dynmapWorld, u));

        byte[] bytes = u.toJSONString().getBytes("UTF-8");

        String dateStr = new Date().toString();
        response.fields.put(HttpField.Date, dateStr);
        response.fields.put(HttpField.ContentType, "text/plain; charset=utf-8");
        response.fields.put(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
        response.fields.put(HttpField.LastModified, dateStr);
        response.fields.put(HttpField.ContentLength, Integer.toString(bytes.length));
        response.status = HttpStatus.OK;

        BufferedOutputStream out = null;
        out = new BufferedOutputStream(response.getBody());
        out.write(bytes);
        out.flush();
    }
}
