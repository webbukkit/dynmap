package org.dynmap.web;

import java.io.IOException;

public interface HttpHandler {
    void handle(String path, HttpRequest request, HttpResponse response) throws IOException;
}
