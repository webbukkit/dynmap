package org.dynmap.servlet;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dynmap.DynmapCore;

public class ConfigJSServlet extends HttpServlet {
    private static final long serialVersionUID = 3543457384759837L;
    private byte[] outputBytes;

    public ConfigJSServlet(DynmapCore core) {
        Charset cs_utf8 = Charset.forName("UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("var config = {\n");
        sb.append(" url : {\n");
        /* Get configuration URL */
        sb.append("  configuration: '");
        sb.append(core.configuration.getString("url/configuration", "up/configuration"));
        sb.append("',\n");
        /* Get update URL */
        sb.append("  update: '");
        sb.append(core.configuration.getString("url/update", "up/world/{world}/{timestamp}"));
        sb.append("',\n");
        /* Get sendmessage URL */
        sb.append("  sendmessage: '");
        sb.append(core.configuration.getString("url/sendmessage", "up/sendmessage"));
        sb.append("',\n");
        /* Get login URL */
        sb.append("  login: '");
        sb.append(core.configuration.getString("url/login", "up/login"));
        sb.append("',\n");
        /* Get register URL */
        sb.append("  register: '");
        sb.append(core.configuration.getString("url/register", "up/register"));
        sb.append("',\n");
        /* Get tiles URL */
        sb.append("  tiles: '");
        sb.append(core.configuration.getString("url/tiles", "tiles/"));
        sb.append("',\n");
        /* Get markers URL */
        sb.append("  markers: '");
        sb.append(core.configuration.getString("url/markers", "tiles/"));
        sb.append("'\n }\n};\n");
        outputBytes = sb.toString().getBytes(cs_utf8);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String dateStr = new Date().toString();
        res.addHeader("Date", dateStr);
        res.setContentType("text/javascript; charset=utf-8");
        res.addHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        res.addHeader("Last-modified", dateStr);
        res.setContentLength(outputBytes.length);
        res.getOutputStream().write(outputBytes);
    }
}
