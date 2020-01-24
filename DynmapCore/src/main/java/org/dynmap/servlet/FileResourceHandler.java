package org.dynmap.servlet;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.Class;

public class FileResourceHandler extends ResourceHandler {
    private static String getNormalizedPath(String p) {
        p = p.replace('\\', '/');
        String[] tok = p.split("/");
        int i, j;
        for(i = 0, j = 0; i < tok.length; i++) {
            if((tok[i] == null) || (tok[i].length() == 0) || (tok[i].equals("."))) {
                tok[i] = null;
            }
            else if(tok[i].equals("..")) {
                if(j > 0) { j--; tok[j] = null;  }
                tok[i] = null;
            }
            else {
                tok[j] = tok[i];
                j++;
            }
        }
        String path = "";
        for(i = 0; i < j; i++) {
            if(tok[i] != null) {
                path = path + "/" + tok[i];
            }
        }
        if (path.length() == 0) {
            path = "/";
        }
        return path;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Resource resource;
        String normalizedTarget = getNormalizedPath(target);
        resource = getResource(normalizedTarget);
        if (resource == null) {
            return;
        }
        File file = resource.getFile();
        if (file == null) {
            return;
        }
    	if(!target.equals(normalizedTarget)){
    		baseRequest.setURIPathQuery(normalizedTarget);
    		baseRequest.setPathInfo(normalizedTarget);
    		try{
    			Class<?> requestClass = request.getClass();
    			Field field = requestClass.getDeclaredField("_pathInfo");
    			field.setAccessible(true);
    			field.set(request, normalizedTarget);
    		} catch (Exception ignore) {
    			//It's unsafe to continue since these lines will be triggered by only malicious requests.
    			ignore.printStackTrace();
    			return;
    		}
    	}
    	super.handle(normalizedTarget, baseRequest, request, response);
    }
}
