package org.dynmap.servlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.dynmap.Log;

public class MainServlet extends HttpServlet {
    public static class Header {
        public String name;
        public String value;
        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
    
    private static class Registration {
        public String pattern;
        public HttpServlet servlet;
        
        public Registration(String pattern, HttpServlet servlet) {
            this.pattern = pattern;
            this.servlet = servlet;
        }
    }
    
    List<Registration> registrations = new LinkedList<Registration>();
    public List<Header> customHeaders = new LinkedList<Header>();
    
    public void addServlet(String pattern, HttpServlet servlet) {
        registrations.add(new Registration(pattern, servlet));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        String path = req.getPathInfo();
        
        for(Header header : customHeaders) {
            resp.setHeader(header.name, header.value);
        }
        
        Registration bestMatch = null;
        String bestMatchPart = null;
        HashMap<String, Object> bestProperties = null;
        
        for (Registration r : registrations) {
            String matchingPart = match(r.pattern, path, properties);
            if (matchingPart != null) {
                if (bestMatchPart == null || bestMatchPart.length() < matchingPart.length()) {
                    bestMatch = r;
                    bestMatchPart = matchingPart;
                    bestProperties = properties;
                    properties = new HashMap<String, Object>();
                }
            }
        }
        if (bestMatch == null) {
            resp.sendError(404);
        } else {
            String leftOverPath = path.substring(bestMatchPart.length());
            HttpServletRequest newreq = new RequestWrapper(req, leftOverPath);
            for(String key : bestProperties.keySet()) {
                newreq.setAttribute(key,  bestProperties.get(key));
            }
            bestMatch.servlet.service(newreq, resp);
        }
    }
    
    public String match(String pattern, String path, Map<String, Object> properties) {
        int patternStart = 0;
        int pathStart = 0;
        while (patternStart < pattern.length()) {
            if (pattern.charAt(patternStart) == '{') {
                // Found a variable.
                int endOfVariable = pattern.indexOf('}', patternStart+1);
                String variableName = pattern.substring(patternStart+1, endOfVariable);
                
                int endOfSection = indexOfAny(path, new char[] { '/', '?' }, pathStart);
                if (endOfSection < 0) {
                    endOfSection = path.length();
                }
                String variableValue = path.substring(pathStart, endOfSection);
                
                // Store variable.
                properties.put(variableName, variableValue);
                
                patternStart = endOfVariable+1;
                pathStart = endOfSection;
            } else {
                int endOfLiteral = pattern.indexOf('{', patternStart);
                if (endOfLiteral < 0) {
                    endOfLiteral = pattern.length();
                }
                String literal = pattern.substring(patternStart, endOfLiteral);
                int endOfPathLiteral = pathStart + literal.length();
                if (endOfPathLiteral > path.length()) {
                    return null;
                }
                String matchingLiteral = path.substring(pathStart, endOfPathLiteral);
                if (!literal.equals(matchingLiteral)) {
                    return null;
                }
                
                patternStart = endOfLiteral;
                pathStart = endOfPathLiteral;
            }
        }
        // Return the part of the url that matches the pattern. (if the pattern does not contain any variables, this will be equal to the pattern)
        return path.substring(0, pathStart);
    }
    
    private int indexOfAny(String s, char[] cs, int startIndex) {
        for(int i = startIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            for(int j = 0; j < cs.length; j++) {
                if (c == cs[j]) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    class RequestWrapper extends HttpServletRequestWrapper {
        String pathInfo;
        public RequestWrapper(HttpServletRequest request, String pathInfo) {
            super(request);
            this.pathInfo = pathInfo;
        }
        @Override
        public String getPathInfo() {
            return pathInfo;
        }
    }
}
