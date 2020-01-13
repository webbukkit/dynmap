package org.dynmap.web;

import org.dynmap.ConfigurationNode;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomHeaderFilter implements Filter {
    private final ConfigurationNode custhttp;
    public CustomHeaderFilter(ConfigurationNode configuration) {
        this.custhttp = configuration;
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse resp = (HttpServletResponse) response;

        if (custhttp != null) {
            for (String k : custhttp.keySet()) {
                String v = custhttp.getString(k);
                if (v != null) {
                    resp.setHeader(k, v);
                }
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
