package org.dynmap.web.handlers;

import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;

import org.dynmap.Event;
import org.dynmap.web.HttpField;
import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpMethod;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.dynmap.web.HttpStatus;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class SendMessageHandler implements HttpHandler {
    protected static final Logger log = Logger.getLogger("Minecraft");
    protected static final String LOG_PREFIX = "[dynmap] ";

    private static final JSONParser parser = new JSONParser();
    public Event<Message> onMessageReceived = new Event<SendMessageHandler.Message>();
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
    
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {
        if (!request.method.equals(HttpMethod.Post)) {
            response.status = HttpStatus.MethodNotAllowed;
            response.fields.put(HttpField.Accept, HttpMethod.Post);
            return;
        }

        InputStreamReader reader = new InputStreamReader(request.body, cs_utf8);

        JSONObject o = (JSONObject)parser.parse(reader);
        final Message message = new Message();
        
        if(trustclientname) {
            message.name = String.valueOf(o.get("name"));
        }
        else {
        	/* If proxied client address, get original */
        	if(request.fields.containsKey("X-Forwarded-For"))
        		message.name = request.fields.get("X-Forwarded-For");
        	/* If from loopback, we're probably getting from proxy - need to trust client */
        	else if(request.rmtaddr.getAddress().isLoopbackAddress())
        		message.name = String.valueOf(o.get("name"));
        	else
        		message.name = request.rmtaddr.getAddress().getHostAddress();
        }
        if(hideip) {    /* If hiding IP, find or assign alias */
            synchronized(disallowedUsersLock) {
                String n = useralias.get(message.name);
                if(n == null) { /* Make ID */
                    n = String.format("web-%03d", aliasindex);
                    aliasindex++;
                    useralias.put(message.name, n);
                }
                message.name = n;
            }
        }
        message.message = String.valueOf(o.get("message"));

        final long now = System.currentTimeMillis();

        synchronized(disallowedUsersLock) {
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
                user = new WebUser() {{
                    name = message.name;
                    nextMessageTime = now+maximumMessageInterval;
                }};
                disallowedUsers.put(user.name, user);
                disallowedUserQueue.add(user);
            } else {
                response.fields.put("Content-Length", "0");
                response.status = HttpStatus.Forbidden;
                response.getBody();
                return;
            }
        }

        onMessageReceived.trigger(message);

        response.fields.put(HttpField.ContentLength, "0");
        response.status = HttpStatus.OK;
        response.getBody();
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
