package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;

import org.dynmap.DynmapCore;
import org.json.simple.JSONObject;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private DynmapCore core;
    public static final String USERID_GUEST = "_guest_";
    public static final String USERID_ATTRIB = "userid";
    public static final String LOGIN_PAGE = "../login.html";
    public static final String LOGIN_POST = "/up/login";
    private Charset cs_utf8 = Charset.forName("UTF-8");
    
    public LoginServlet(DynmapCore core) {
        this.core = core;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }
    
    private void sendResponse(HttpServletResponse resp, String rslt) throws ServletException, IOException {
        JSONObject json = new JSONObject();
        s(json, "result", rslt);
        byte[] b = json.toJSONString().getBytes(cs_utf8);
        String dateStr = new Date().toString();
        resp.addHeader("Date", dateStr);
        resp.setContentType("text/plain; charset=utf-8");
        resp.addHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        resp.addHeader("Last-modified", dateStr);
        resp.setContentLength(b.length);
        resp.getOutputStream().write(b);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        /* Get session - initialize if needed */
        HttpSession sess = req.getSession(true);
        String uid = (String)sess.getAttribute(USERID_ATTRIB);
        if(uid == null) {
            uid = USERID_GUEST;
            sess.setAttribute(USERID_ATTRIB, uid);   /* Set to guest access */
        }
        if(sess.isNew()) {
            sess.setMaxInactiveInterval(60);    /* Intialize to 60 seconds */
        }
        
        String uri = req.getRequestURI();
        if(uri.equals("/up/login")) {  /* Process login form */
            uid = req.getParameter("j_username");
            String pwd = req.getParameter("j_password");
            if((uid == null) || (uid.equals("")))
                uid = USERID_GUEST;
            if(core.checkLogin(uid, pwd)) {
                sess.setAttribute(USERID_ATTRIB, uid);
                sendResponse(resp, "success");
            }
            else {
                sendResponse(resp, "loginfailed");
            }
        }
        else if(uri.equals("/up/register")) {  /* Process register form */
            uid = req.getParameter("j_username");
            String pwd = req.getParameter("j_password");
            String vpwd = req.getParameter("j_verify_password");
            String passcode = req.getParameter("j_passcode");
            if((pwd == null) || (vpwd == null) || (pwd.equals(vpwd) == false)) {
                resp.sendRedirect(LOGIN_PAGE + "?error=verifyfailed");
                sendResponse(resp, "verifyfailed");
            }
            else if(core.registerLogin(uid, pwd, passcode)) {    /* Good registration? */
                sess.setAttribute(USERID_ATTRIB, uid);
                sendResponse(resp, "success");
            }
            else {
                sendResponse(resp, "registerfailed");
            }
        }
        else {
            sendResponse(resp, "loginfailed");
        }
    }

    @Override
    public void destroy() { }
}
