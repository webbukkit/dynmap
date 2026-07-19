package org.dynmap.web;

import org.dynmap.DynmapCore;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class FileNameFilter implements Filter {

    public FileNameFilter(DynmapCore core) {
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    	String path = ((HttpServletRequest)request).getRequestURL().toString();
    	HttpServletResponse resp = (HttpServletResponse)response;
        // Filter unneeded file requests
        if (path.toLowerCase().endsWith(".php")) {
            resp.sendError(404);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() { }
}
