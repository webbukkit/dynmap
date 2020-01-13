package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;
import static org.dynmap.JSONUtils.g;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.dynmap.Client;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.InternalClientUpdateComponent;
import org.dynmap.web.HttpField;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

@SuppressWarnings("serial")
public class ClientUpdateServlet extends HttpServlet {
    private DynmapCore core;
    private Charset cs_utf8 = Charset.forName("UTF-8");
    
    public ClientUpdateServlet(DynmapCore plugin) {
        this.core = plugin;
    }

    Pattern updatePathPattern = Pattern.compile("/([^/]+)/([0-9]*)");
    @SuppressWarnings("unchecked")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        byte[] bytes;
        HttpSession sess = req.getSession(true);
        String user = (String) sess.getAttribute(LoginServlet.USERID_ATTRIB);
        if(user == null) user = LoginServlet.USERID_GUEST;
        boolean guest = user.equals(LoginServlet.USERID_GUEST);
        if(core.getLoginRequired() && guest) {
            JSONObject json = new JSONObject();
            s(json, "error", "login-required");
            bytes = json.toJSONString().getBytes(cs_utf8);
        }
        else {
            String path = req.getPathInfo();
            Matcher match = updatePathPattern.matcher(path);
        
            if (!match.matches()) {
                resp.sendError(404, "World not found");
                return;
            }
        
            String worldName = match.group(1);
            String timeKey = match.group(2);
        
            DynmapWorld dynmapWorld = null;
            if(core.mapManager != null) {
                dynmapWorld = core.mapManager.getWorld(worldName);
            }
            if (dynmapWorld == null) {
                resp.sendError(404, "World not found");
                return;
            }
            long since = 0;

            try {
                since = Long.parseLong(timeKey);
            } catch (NumberFormatException e) {
            }

            JSONObject u = new JSONObject();
            //s(u, "timestamp", current);
            JSONObject upd = InternalClientUpdateComponent.getWorldUpdate(dynmapWorld.getName());
            if(upd != null)
                u.putAll(upd);
            boolean see_all = true;
            if(core.player_info_protected) {
                if(guest) {
                    see_all = false;
                }
                else {
                    see_all = core.getServer().checkPlayerPermission(user, "playermarkers.seeall");
                }
            }
            if(!see_all) {
                JSONArray players = (JSONArray)g(u, "players");
                JSONArray newplayers = new JSONArray();
                u.put("players",  newplayers);
                if(players != null) {
                    for(ListIterator<JSONObject> iter = players.listIterator(); iter.hasNext();) {
                        JSONObject p = iter.next();
                        JSONObject newp = new JSONObject();
                        newp.putAll(p);
                        newplayers.add(newp);
                        boolean hide;
                        if(!guest) {
                            hide = !core.testIfPlayerVisibleToPlayer(user, (String)newp.get("name"));
                        }
                        else {
                            hide = true;
                        }
                        if(hide) {
                            s(newp, "world", "-some-other-bogus-world-");
                            s(newp, "x", 0.0);
                            s(newp, "y", 64.0);
                            s(newp, "z", 0.0);
                            s(newp, "health", 0);
                            s(newp, "armor", 0);
                        }
                    }
                }
            }
            JSONArray updates = (JSONArray)u.get("updates");
            JSONArray newupdates = new JSONArray();
            u.put("updates", newupdates);
            if(updates != null) {
                for(ListIterator<Client.Update> iter = updates.listIterator(); iter.hasNext();) {
                    Client.Update update = iter.next();
                    if(update.timestamp >= since) {
                        newupdates.add(update);
                    }
                }
            }
            bytes = u.toJSONString().getBytes(cs_utf8);
        }
        
        String dateStr = new Date().toString();
        resp.addHeader(HttpField.Date, dateStr);
        resp.addHeader(HttpField.ContentType, "text/plain; charset=utf-8");
        resp.addHeader(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
        resp.addHeader(HttpField.LastModified, dateStr);
        resp.addHeader(HttpField.ContentLength, Integer.toString(bytes.length));

        resp.getOutputStream().write(bytes);
    }
}
