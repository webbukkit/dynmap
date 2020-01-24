package org.dynmap.web;

import org.eclipse.jetty.http.PathMap;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HandlerRouter extends AbstractHandler {

    PathMap pathMap = new PathMap();
    public HandlerRouter() {
    }
    
    public void addHandler(String path, Handler handler) {
        pathMap.put(path, handler);
    }
    
    public void addServlet(String path, Servlet servlet) {
        pathMap.put(path, servlet);
    }
    
    public void clear() {
        pathMap.clear();
    }
    
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String pathInfo = request.getPathInfo();
        PathMap.MappedEntry e = pathMap.getMatch(pathInfo);
        String mappedPath = e.getMapped();

        String childPathInfo = pathInfo;
        if (mappedPath != null) {
            int i = 0;
            while(i<mappedPath.length() && mappedPath.charAt(i) == pathInfo.charAt(i)){ i++; }
            childPathInfo = childPathInfo.substring(i);
        }

        org.eclipse.jetty.server.Request r = (org.eclipse.jetty.server.Request)request;
        r.setPathInfo(childPathInfo);

        Object o = e.getValue();
        if (o instanceof Handler) {
            Handler h = (Handler)o;
            h.handle(target, baseRequest, request, response);
        } else if (o instanceof Servlet) {
            Servlet s = (Servlet)o;
            s.service(request, response);
        }

        r.setPathInfo(pathInfo);
    }
}
