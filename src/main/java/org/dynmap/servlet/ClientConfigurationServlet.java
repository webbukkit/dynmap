package org.dynmap.servlet;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Event;
import org.json.simple.JSONObject;

public class ClientConfigurationServlet extends HttpServlet {
    private static final long serialVersionUID = 9106801553080522469L;
    private DynmapCore plugin;
    private byte[] cachedConfiguration = null;
    private int cached_config_hashcode = 0;

    public ClientConfigurationServlet(DynmapCore plugin) {
        this.plugin = plugin;
        plugin.events.addListener("worldactivated", new Event.Listener<DynmapWorld>() {
            @Override
            public void triggered(DynmapWorld t) {
                cachedConfiguration = null;
            }
        });
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        byte[] outputBytes = cachedConfiguration;
        if ((outputBytes == null) || (cached_config_hashcode != plugin.getConfigHashcode())) {
            JSONObject json = new JSONObject();
            plugin.events.<JSONObject>trigger("buildclientconfiguration", json);

            String s = json.toJSONString();

            outputBytes = s.getBytes("UTF-8");
        }
        if (outputBytes != null) {
            cachedConfiguration = outputBytes;
            cached_config_hashcode = plugin.getConfigHashcode();
        }
        String dateStr = new Date().toString();
        res.addHeader("Date", dateStr);
        res.setContentType("text/plain; charset=utf-8");
        res.addHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        res.addHeader("Last-modified", dateStr);
        res.setContentLength(outputBytes.length);
        res.getOutputStream().write(outputBytes);
    }
}
