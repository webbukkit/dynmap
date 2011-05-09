package org.dynmap.web.handlers;

import java.io.InputStreamReader;
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
    
    private static final JSONParser parser = new JSONParser();
    public Event<Message> onMessageReceived = new Event<SendMessageHandler.Message>();
    
    public int maximumMessageInterval = 1000;
    public String spamMessage = "\"You may only chat once every %interval% seconds.\"";
    private HashMap<String, WebUser> disallowedUsers = new HashMap<String, WebUser>();
    private LinkedList<WebUser> disallowedUserQueue = new LinkedList<WebUser>();
    private Object disallowedUsersLock = new Object();
    
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {
        if (!request.method.equals(HttpMethod.Post)) {
            response.status = HttpStatus.MethodNotAllowed;
            response.fields.put(HttpField.Accept, HttpMethod.Post);
            return;
        }

        InputStreamReader reader = new InputStreamReader(request.body);
        
        JSONObject o = (JSONObject)parser.parse(reader);
        final Message message = new Message();
        message.name = String.valueOf(o.get("name"));
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
