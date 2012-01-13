package org.dynmap.web;

import org.dynmap.DynmapPlugin;
import org.dynmap.Log;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;

public class BanIPFilter implements Filter {
    private DynmapPlugin plugin;
    private HashSet<String> banned_ips = new HashSet<String>();
    private HashSet<String> banned_ips_notified = new HashSet<String>();
    private long last_loaded = 0;
    private long lastmod = 0;
    private static final long BANNED_RELOAD_INTERVAL = 15000;	/* Every 15 seconds */

    public BanIPFilter(DynmapPlugin plugin) {
        this.plugin = plugin;
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
        banned_ips.clear();
        banned_ips_notified.clear();
        banned_ips.addAll(plugin.getIPBans());
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
