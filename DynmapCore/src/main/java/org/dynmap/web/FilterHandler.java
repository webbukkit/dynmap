package org.dynmap.web;

import org.dynmap.Log;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.FilterHolder;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

public class FilterHandler extends AbstractHandler {
    private Handler handler;
    private LinkedList<FilterHolder> filters = new LinkedList<FilterHolder>();

    public FilterHandler() {
    }
    
    public FilterHandler(Handler handler, Iterable<Filter> filters) {
        this.handler = handler;
        for(Filter f : filters) {
            try {
                FilterHolder holder = new FilterHolder(f);
                holder.start();
                holder.initialize();
                this.filters.add(holder);
            }catch (Exception e){
                Log.severe("Failed to initialize filter holder: "+e.toString());
            }
        }
    }
    
    public Handler getHandler() {
        return handler;
    }
    
    public void setHandler(Handler handler) {
        this.handler = handler;
    }
    
    public Iterable<FilterHolder> getFilters() {
        return filters;
    }
    
    public void addFilter(Filter filter) {
        filters.add(new FilterHolder(filter));
    }

    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        final Handler handler = this.getHandler();
        final Iterator<FilterHolder> iterator = getFilters().iterator();
        final FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest re, ServletResponse rs) throws IOException, ServletException {
                if (iterator.hasNext()) {
                    Filter f = iterator.next().getFilter();
                    f.doFilter(request, response, this);
                } else {
                    handler.handle(target, baseRequest, request, response);
                }
            }
        };
        chain.doFilter(request, response);
    }
}
