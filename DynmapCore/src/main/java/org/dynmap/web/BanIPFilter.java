package org.dynmap.web;

import org.dynmap.DynmapCore;
import org.dynmap.Log;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BanIPFilter implements Filter {
    private DynmapCore core;
    private Set<String> banned_ips = null;
    private HashSet<String> banned_ips_notified = new HashSet<String>();
    private long last_loaded = 0;
    private static final long BANNED_RELOAD_INTERVAL = 15000;	/* Every 15 seconds */

    public BanIPFilter(DynmapCore core) {
        this.core = core;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse resp = (HttpServletResponse)response;
        String ipaddr = request.getRemoteAddr();
        if (isIpBanned(ipaddr)) {
            Log.info("Rejected connection by banned IP address - " + ipaddr);
            resp.sendError(403);
        } else {
            chain.doFilter(request, response);
        }
    }

    private void loadBannedIPs() {
        banned_ips_notified.clear();
        banned_ips = core.getIPBans();
    }

    /* Return true if address is banned */
    public boolean isIpBanned(String ipaddr) {
        long t = System.currentTimeMillis();
        if((t < last_loaded) || ((t-last_loaded) > BANNED_RELOAD_INTERVAL)) {
            loadBannedIPs();
            last_loaded = t;
        }
        if(banned_ips.contains(ipaddr)) {
            if(!banned_ips_notified.contains(ipaddr)) {
                banned_ips_notified.add(ipaddr);
            }
            return true;
        }
        return false;
    }

    @Override
    public void destroy() { }
}
