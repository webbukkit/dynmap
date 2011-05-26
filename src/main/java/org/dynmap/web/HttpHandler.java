package org.dynmap.web;


public interface HttpHandler {
    void handle(String path, HttpRequest request, HttpResponse response) throws Exception;
}
