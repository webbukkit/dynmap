package org.dynmap.servlet;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.MapType.ImageEncoding;
import org.dynmap.PlayerFaces;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTile.TileRead;
import org.dynmap.utils.BufferInputStream;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStream;

public class MapStorageResourceHandler extends AbstractHandler {

    private DynmapCore core;
    private byte[] blankpng;
    private long blankpnghash = 0x12345678;
    
    public MapStorageResourceHandler() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedImage blank = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        try {
            ImageIO.write(blank, "png", baos);
            blankpng = baos.toByteArray();
        } catch (IOException e) {
        }
        
    }
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = baseRequest.getPathInfo();
        int soff = 0, eoff;
        // We're handling this request
        baseRequest.setHandled(true);
        if(core.getLoginRequired()
            && request.getSession(true).getAttribute(LoginServlet.USERID_ATTRIB) == null){
            response.sendError(HttpStatus.UNAUTHORIZED_401);
            return;
        }
        if (path.charAt(0) == '/') soff = 1;
        eoff = path.indexOf('/', soff);
        if (soff < 0) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        String world = path.substring(soff, eoff);
        String uri = path.substring(eoff+1);
        // If faces directory, handle faces
        if (world.equals("faces")) {
            handleFace(response, uri);
            return;
        }
        // If markers directory, handle markers
        if (world.equals("_markers_")) {
            handleMarkers(response, uri);
            return;
        }

        DynmapWorld w = null;
        if (core.mapManager != null) {
            w = core.mapManager.getWorld(world);
        }
        // If world not found quit
        if (w == null) {
            response.setContentType("image/png");
            OutputStream os = response.getOutputStream();
            os.write(blankpng);
            return;
        }
        MapStorage store = w.getMapStorage();    // Get storage handler
        // Get tile reference, based on URI and world
        MapStorageTile tile = store.getTile(w, uri);
        if (tile == null) {
            response.setContentType("image/png");
            OutputStream os = response.getOutputStream();
            os.write(blankpng);
            return;
        }
        // Read tile
        TileRead tr = null;
        if (tile.getReadLock(5000)) {
            tr = tile.read();
            tile.releaseReadLock();
        }
        response.setHeader("Cache-Control", "max-age=0,must-revalidate");
        String etag;
        if (tr == null) {
        	etag = "\"" + blankpnghash + "\"";
        }
        else {
        	etag = "\"" + tr.hashCode + "\"";
        }
        response.setHeader("ETag", etag);
        String ifnullmatch = request.getHeader("If-None-Match");
        if ((ifnullmatch != null) && ifnullmatch.equals(etag)) {
            response.sendError(HttpStatus.NOT_MODIFIED_304);
        	return;
        }
        if (tr == null) {
            response.setContentType("image/png");
            response.setIntHeader("Content-Length", blankpng.length);
            OutputStream os = response.getOutputStream();
            os.write(blankpng);
            return;
        }
        // Got tile, package up for response
        response.setDateHeader("Last-Modified", tr.lastModified);
        response.setIntHeader("Content-Length", tr.image.length());
        response.setContentType(tr.format.getContentType());
        ServletOutputStream out = response.getOutputStream();
        out.write(tr.image.buffer(), 0, tr.image.length());
        out.flush();

    }

    private void handleFace(HttpServletResponse response, String uri) throws IOException, ServletException {
        String[] suri = uri.split("[/\\.]");
        if (suri.length < 3) {  // 3 parts : face ID, player name, png
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        // Find type
        PlayerFaces.FaceType ft = PlayerFaces.FaceType.byID(suri[0]);
        if (ft == null) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        BufferInputStream bis = null;
        if (core.playerfacemgr != null) {
            bis = core.playerfacemgr.storage.getPlayerFaceImage(suri[1], ft);
        }
        if (bis == null) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        // Got image, package up for response
        response.setIntHeader("Content-Length", bis.length());
        response.setContentType("image/png");
        ServletOutputStream out = response.getOutputStream();
        out.write(bis.buffer(), 0, bis.length());
        out.flush();
    }

    private void handleMarkers(HttpServletResponse response, String uri) throws IOException, ServletException {
        String[] suri = uri.split("/");
        // If json file in last part
        if ((suri.length == 1) && suri[0].startsWith("marker_") && suri[0].endsWith(".json")) {
            String content = core.getDefaultMapStorage().getMarkerFile(suri[0].substring(7, suri[0].length() - 5));
            response.setContentType("application/json");
            PrintWriter pw = response.getWriter();
            pw.print(content);
            pw.flush();
            return;
        }
        // If png, make marker ID
        if (suri[suri.length-1].endsWith(".png")) {
            BufferInputStream bis = core.getDefaultMapStorage().getMarkerImage(uri.substring(0, uri.length()-4));
            // Got image, package up for response
            response.setIntHeader("Content-Length", bis.length());
            response.setContentType("image/png");
            ServletOutputStream out = response.getOutputStream();
            out.write(bis.buffer(), 0, bis.length());
            out.flush();
            return;
        }
        response.sendError(HttpStatus.NOT_FOUND_404);
    }

    public void setCore(DynmapCore core) {
        this.core = core;
    }
}
