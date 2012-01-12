package org.dynmap.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONAware;
import org.json.simple.JSONStreamAware;

public class JSONServlet {
    public static void respond(HttpServletResponse response, JSONStreamAware json) throws IOException {
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();
        json.writeJSONString(writer);
        writer.close();
    }
}
