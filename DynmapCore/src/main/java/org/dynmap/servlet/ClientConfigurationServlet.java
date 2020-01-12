package org.dynmap.servlet;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.InternalClientUpdateComponent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.dynmap.JSONUtils.g;
import static org.dynmap.JSONUtils.s;

public class ClientConfigurationServlet extends HttpServlet {
    private static final long serialVersionUID = 9106801553080522469L;
    private DynmapCore core;
    private Charset cs_utf8 = StandardCharsets.UTF_8;

    public ClientConfigurationServlet(DynmapCore plugin) {
        this.core = plugin;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        byte[] outputBytes;

        HttpSession sess = req.getSession(true);
        String user = (String) sess.getAttribute(LoginServlet.USERID_ATTRIB);
        if (user == null) user = LoginServlet.USERID_GUEST;
        boolean guest = user.equals(LoginServlet.USERID_GUEST);
        JSONObject json = new JSONObject();
        if (core.getLoginRequired() && guest) {
            s(json, "error", "login-required");
        } else if (core.isLoginSupportEnabled()) {
            if (guest) {
                s(json, "loggedin", false);
            } else {
                s(json, "loggedin", true);
                s(json, "player", user);
            }
            JSONObject obj = InternalClientUpdateComponent.getClientConfig();
            if (obj != null) {
                json.putAll(obj);
            }
            /* Prune based on security */
            JSONArray wlist = (JSONArray) g(json, "worlds");
            JSONArray newwlist = new JSONArray();
            json.put("worlds", newwlist);
            for (JSONObject w : (Iterable<JSONObject>) wlist) {
                String n = (String) g(w, "name");
                DynmapWorld dw = core.getWorld(n);
                /* If protected, and we're guest or don't have permission, drop it */
                if (dw.isProtected() && (guest || (!core.getServer().checkPlayerPermission(user, "world." + n)))) {
                    /* Don't add to new list */
                } else {
                    JSONObject neww = new JSONObject();
                    neww.putAll(w);
                    newwlist.add(neww);
                    JSONArray mlist = (JSONArray) g(w, "maps");
                    JSONArray newmlist = new JSONArray();
                    neww.put("maps", newmlist);
                    for (JSONObject m : (Iterable<JSONObject>) mlist) {
                        Boolean prot = (Boolean) g(m, "protected");
                        /* If not protected, leave it in */
                        if ((prot == null) || (!prot.booleanValue())) {
                            newmlist.add(m);
                            continue;
                        }
                        /* If not guest and we have permission, keep it */
                        String mn = (String) g(m, "name");
                        if ((!guest) && core.getServer().checkPlayerPermission(user, "map." + n + "." + mn)) {
                            newmlist.add(m);
                        }
                    }
                }
            }
        }
        else { 
            s(json, "loggedin", !guest);
            JSONObject obj = InternalClientUpdateComponent.getClientConfig();
            if(obj != null) {
                json.putAll(obj);
            }
        }
        outputBytes = json.toJSONString().getBytes(cs_utf8);

        String dateStr = new Date().toString();
        res.addHeader("Date", dateStr);
        res.setContentType("text/plain; charset=utf-8");
        res.addHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        res.addHeader("Last-modified", dateStr);
        res.setContentLength(outputBytes.length);
        res.getOutputStream().write(outputBytes);
    }
}
