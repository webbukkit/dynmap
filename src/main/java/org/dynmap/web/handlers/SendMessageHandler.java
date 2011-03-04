package org.dynmap.web.handlers;

import java.io.InputStreamReader;
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
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {
        if (!request.method.equals(HttpMethod.Post)) {
            response.status = HttpStatus.MethodNotAllowed;
            response.fields.put(HttpField.Accept, HttpMethod.Post);
            return;
        }

        InputStreamReader reader = new InputStreamReader(request.body);
        
        JSONObject o = (JSONObject)parser.parse(reader);
        Message message = new Message();
        message.name = String.valueOf(o.get("name"));
        message.message = String.valueOf(o.get("message"));
        
        onMessageReceived.trigger(message);
        
        response.fields.put(HttpField.ContentLength, "0");
        response.status = HttpStatus.OK;
        response.getBody();
    }
    public class Message {
        public String name;
        public String message;
    }
}
