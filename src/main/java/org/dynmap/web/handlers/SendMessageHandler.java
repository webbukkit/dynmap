package org.dynmap.web.handlers;

import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.util.logging.Logger;

import org.dynmap.Event;
import org.dynmap.web.HttpErrorHandler;
import org.dynmap.web.HttpField;
import org.dynmap.web.HttpHandler;
import org.dynmap.web.HttpMethods;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class SendMessageHandler implements HttpHandler {
    protected static final Logger log = Logger.getLogger("Minecraft");
    
    private static final JSONParser parser = new JSONParser();
    public Event<Message> onMessageReceived = new Event<SendMessageHandler.Message>();
    @Override
    public void handle(String path, HttpRequest request, HttpResponse response) throws Exception {
        if (!request.method.equals(HttpMethods.Post)) {
            HttpErrorHandler.handleMethodNotAllowed(response);
            return;
        }

        InputStreamReader reader = new InputStreamReader(request.body);
        
        JSONObject o = (JSONObject)parser.parse(reader);
        Message message = new Message();
        message.name = String.valueOf(o.get("name"));
        message.message = String.valueOf(o.get("message"));
        
        onMessageReceived.trigger(message);
        
        response.fields.put(HttpField.contentLength, "0");
        response.getBody();
    }
    public class Message {
        public String name;
        public String message;
    }
}
