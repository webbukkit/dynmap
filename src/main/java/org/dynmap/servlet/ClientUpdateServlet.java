package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dynmap.ClientUpdateEvent;
import org.dynmap.DynmapPlugin;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.web.HttpField;
import org.json.simple.JSONObject;

public class ClientUpdateServlet extends HttpServlet {
    private DynmapPlugin plugin;
    
    public ClientUpdateServlet(DynmapPlugin plugin) {
        this.plugin = plugin;
    }

    Pattern updatePathPattern = Pattern.compile("/([^/]+)/([0-9]*)");
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        Matcher match = updatePathPattern.matcher(path);
        
        if (!match.matches()) {
            resp.sendError(404, "World not found");
            return;
        }
        
        String worldName = match.group(1);
        String timeKey = match.group(2);
        
        DynmapWorld dynmapWorld = null;
        if(plugin.mapManager != null) {
            dynmapWorld = plugin.mapManager.getWorld(worldName);
        }
        if (dynmapWorld == null || dynmapWorld.world == null) {
            resp.sendError(404, "World not found");
            return;
        }
        long current = System.currentTimeMillis();
        long since = 0;

        try {
            since = Long.parseLong(timeKey);
        } catch (NumberFormatException e) {
        }

        JSONObject u = new JSONObject();
        s(u, "timestamp", current);
        plugin.events.trigger("buildclientupdate", new ClientUpdateEvent(since, dynmapWorld, u));

        byte[] bytes = u.toJSONString().getBytes("UTF-8");

        String dateStr = new Date().toString();
        resp.addHeader(HttpField.Date, dateStr);
        resp.addHeader(HttpField.ContentType, "text/plain; charset=utf-8");
        resp.addHeader(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
        resp.addHeader(HttpField.LastModified, dateStr);
        resp.addHeader(HttpField.ContentLength, Integer.toString(bytes.length));

        resp.getOutputStream().write(bytes);
    }
}
