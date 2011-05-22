package org.dynmap.web;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.net.InetSocketAddress;

public class HttpRequest {
    public String method;
    public String path;
    public String version;
    public Map<String, String> fields = new HashMap<String, String>();
    public InputStream body;
    public InetSocketAddress rmtaddr;
}
