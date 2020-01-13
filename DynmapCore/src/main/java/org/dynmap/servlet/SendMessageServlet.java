package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;

import org.dynmap.DynmapCore;
import org.dynmap.Event;
import org.dynmap.Log;
import org.dynmap.web.HttpField;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

@SuppressWarnings("serial")
public class SendMessageServlet extends HttpServlet {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private static final JSONParser parser = new JSONParser();
    public Event<Message> onMessageReceived = new Event<Message>();
    private Charset cs_utf8 = Charset.forName("UTF-8");
    public int maximumMessageInterval = 1000;
    public boolean hideip = false;
    public boolean trustclientname = false;
    
    public String spamMessage = "\"You may only chat once every %interval% seconds.\"";
    private HashMap<String, WebUser> disallowedUsers = new HashMap<String, WebUser>();
    private LinkedList<WebUser> disallowedUserQueue = new LinkedList<WebUser>();
    private Object disallowedUsersLock = new Object();
    private HashMap<String,String> useralias = new HashMap<String,String>();
    private int aliasindex = 1;
    public boolean use_player_login_ip = false;
    public boolean require_player_login_ip = false;
    public boolean check_user_ban = false;
    public boolean require_login = false;
    public boolean chat_perms = false;
    public int lengthlimit = 256;
    public DynmapCore core;
    public HashSet<String> proxyaddress = new HashSet<String>();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        byte[] bytes;
        String error = "none";
        HttpSession sess = request.getSession(true);
        String userID = (String) sess.getAttribute(LoginServlet.USERID_ATTRIB);
        if(userID == null) userID = LoginServlet.USERID_GUEST;
        boolean chat_requires_login = core.getLoginRequired() || require_login;
        if(chat_requires_login && userID.equals(LoginServlet.USERID_GUEST)) {
            error = "login-required";
        }
        else if(chat_requires_login && (!userID.equals(LoginServlet.USERID_GUEST)) && chat_perms && 
                (!core.checkPermission(userID, "webchat"))) {
            Log.info("Rejected web chat by " + userID + ": not permitted");
            error = "not-permitted";
        }
        else {
            boolean ok = true;
            
            InputStreamReader reader = new InputStreamReader(request.getInputStream(), cs_utf8);

            JSONObject o = null;
            try {
                o = (JSONObject)parser.parse(reader);
            } catch (ParseException e) {
                error = "bad-format";
                ok = false;
            }

            final Message message = new Message();

            if (userID.equals(LoginServlet.USERID_GUEST)) {
                message.name = "";
                if (trustclientname) {
                    message.name = String.valueOf(o.get("name"));
                }
                boolean isip = true;
                if ((message.name == null) || message.name.equals("")) {
                    /* If from trusted proxy, check for client */
                    String rmtaddr = request.getRemoteAddr(); 
                    if (this.proxyaddress.contains(rmtaddr)) {
                        /* If proxied client address, get original IP */
                        if (request.getHeader("X-Forwarded-For") != null) {
                            /* If trusted proxies were chained, we get next client address till non-trusted proxy met */
                            String[] proxyAddrs = request.getHeader("X-Forwarded-For").split(", ");
                            for(int i = proxyAddrs.length - 1; i >= 0; i--){
                                if (!this.proxyaddress.contains(proxyAddrs[i])) {
                                    /* use remaining addresses as name (maybe we can use the last or the first non-trusted one?) */
                                    message.name = proxyAddrs[0]; // 0 .. i
                                    for(int j = 1; j <= i; j++) message.name += ", " + proxyAddrs[j];
                                    break;
                                }
                            }
                        } else {
                            message.name = String.valueOf(o.get("name"));
                        }
                    }
                    else
                        message.name = request.getRemoteAddr();
                }
                if (use_player_login_ip) {
                    List<String> ids = core.getIDsForIP(message.name);
                    if (ids != null) {
                        String id = ids.get(0);
                        if (check_user_ban) {
                            if (core.getServer().isPlayerBanned(id)) {
                                Log.info("Ignore message from '" + message.name + "' - banned player (" + id + ")");
                                error = "not-allowed";
                                ok = false;
                            }
                        }
                        if (chat_perms && !core.getServer().checkPlayerPermission(id, "webchat")) {
                            Log.info("Rejected web chat from '" + message.name + "': not permitted (" + id + ")");
                            error = "not-allowed";
                            ok = false;
                        }
                        message.name = id;
                        isip = false;
                    } else if (require_player_login_ip) {
                        Log.info("Ignore message from '" + message.name + "' - no matching player login recorded");
                        error = "not-allowed";
                        ok = false;
                    }
                }
                if (hideip && isip) { /* If hiding IP, find or assign alias */
                    synchronized (disallowedUsersLock) {
                        String n = useralias.get(message.name);
                        if (n == null) { /* Make ID */
                            n = String.format("web-%03d", aliasindex);
                            aliasindex++;
                            useralias.put(message.name, n);
                        }
                        message.name = n;
                    }
                }
            }
            else {
                message.name = userID;
            }
            message.message = String.valueOf(o.get("message"));
            if((lengthlimit > 0) && (message.message.length() > lengthlimit)) {
                message.message = message.message.substring(0, lengthlimit);
            }

            final long now = System.currentTimeMillis();

            synchronized (disallowedUsersLock) {
                // Allow users that  user that are now allowed to send messages.
                while (!disallowedUserQueue.isEmpty()) {
                    WebUser wu = disallowedUserQueue.getFirst();
                    if (now >= wu.nextMessageTime) {
                        disallowedUserQueue.remove();
                        disallowedUsers.remove(wu.name);
                    } else {
                        break;
                    }
                }

                WebUser user = disallowedUsers.get(message.name);
                if (user == null) {
                    user = new WebUser() {
                        {
                            name = message.name;
                            nextMessageTime = now + maximumMessageInterval;
                        }
                    };
                    disallowedUsers.put(user.name, user);
                    disallowedUserQueue.add(user);
                } else {
                    error = "not-allowed";
                    ok = false;
                }
            }
            if(ok)
                onMessageReceived.trigger(message);
        }
        JSONObject json = new JSONObject();
        s(json, "error", error);
        bytes = json.toJSONString().getBytes(cs_utf8);
        
        String dateStr = new Date().toString();
        response.addHeader(HttpField.Date, dateStr);
        response.addHeader(HttpField.ContentType, "text/plain; charset=utf-8");
        response.addHeader(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
        response.addHeader(HttpField.LastModified, dateStr);
        response.addHeader(HttpField.ContentLength, Integer.toString(bytes.length));
        response.getOutputStream().write(bytes);
    }

    public static class Message {
        public String name;
        public String message;
    }
    public static class WebUser {
        public long nextMessageTime;
        public String name;
    }
}
