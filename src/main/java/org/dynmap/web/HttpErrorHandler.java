package org.dynmap.web;

import java.io.IOException;

public class HttpErrorHandler {
    public static void handle(HttpResponse response, int statusCode, String statusMessage) throws IOException {
        response.statusCode = statusCode;
        response.statusMessage = statusMessage;
        response.fields.put("Content-Length", "0");
        response.getBody();
    }
    
    public static void handleNotFound(HttpResponse response) throws IOException {
        handle(response, 404, "Not found");
    }
    
    public static void handleMethodNotAllowed(HttpResponse response) throws IOException {
        handle(response, 405, "Method not allowed");
    }
}
