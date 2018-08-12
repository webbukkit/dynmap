package org.dynmap.exporter;

import java.io.File;
import java.util.HashMap;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.hdmap.HDShader;

/**
 * Handler for export commands (/dynmapexp)
 */
public class DynmapExpCommands {
    private HashMap<String, ExportContext> sessions = new HashMap<String, ExportContext>();

    private static class ExportContext {
        public String shader = "stdtexture";
        public int xmin = Integer.MIN_VALUE;
        public int ymin = Integer.MIN_VALUE;
        public int zmin = Integer.MIN_VALUE;
        public int xmax = Integer.MIN_VALUE;
        public int ymax = Integer.MIN_VALUE;
        public int zmax = Integer.MIN_VALUE;
        public String world;
        public boolean groupByChunk;
        public boolean groupByBlockID;
        public boolean groupByBlockIDData;
        public boolean groupByTexture;
    }

    private String getContextID(DynmapCommandSender sender) {
        String id = "<console>";
        if (sender instanceof DynmapPlayer) {
            id = ((DynmapPlayer)sender).getName();
        }
        return id;
    }
    
    private ExportContext getContext(DynmapCommandSender sender) {
        String id = getContextID(sender);
        
        ExportContext ctx = sessions.get(id);
        if (ctx == null) {
            ctx = new ExportContext();
            sessions.put(id, ctx);
        }
        return ctx;
    }
    
    public boolean processCommand(DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapCore core) {
        /* Re-parse args - handle doublequotes */
        args = DynmapCore.parseArgs(args, sender);
        if(args.length < 1)
            return false;
        if(!core.checkPlayerPermission(sender, "dynmapexp.export")) {
            return true;
        }
        cmd = args[0];
        boolean rslt = false;
        ExportContext ctx = getContext(sender);
        
        if(cmd.equalsIgnoreCase("set")) {
            rslt = handleSetExport(sender, args, ctx, core);
        }
        else if (cmd.equalsIgnoreCase("radius")) {
            rslt = handleRadius(sender, args, ctx, core);
        }
        else if (cmd.equalsIgnoreCase("pos0")) {
            rslt = handlePosN(sender, args, ctx, core, 0);
        }
        else if (cmd.equalsIgnoreCase("pos1")) {
            rslt = handlePosN(sender, args, ctx, core, 1);
        }
        else if (cmd.equalsIgnoreCase("export")) {
            rslt = handleDoExport(sender, args, ctx, core);
        }
        else if(cmd.equalsIgnoreCase("reset")) {
            rslt = handleResetExport(sender, args, ctx, core);
        }
        else if(cmd.equalsIgnoreCase("purge")) {
            rslt = handlePurgeExport(sender, args, ctx, core);
        }
        else if(cmd.equalsIgnoreCase("info")) {
            rslt = handleInfo(sender, args, ctx, core);
        }

        return rslt;
    }

    private boolean handleInfo(DynmapCommandSender sender, String[] args, ExportContext ctx, DynmapCore core) {
        sender.sendMessage(String.format("Bounds: <%s,%s,%s> - <%s,%s,%s> on world '%s'", val(ctx.xmin), val(ctx.ymin), val(ctx.zmin),
                val(ctx.xmax), val(ctx.ymax), val(ctx.zmax), ctx.world));
        sender.sendMessage(String.format("groups: byChunk: %b, byBlockID: %b, byBlockIDData: %b, byTexture: %b", ctx.groupByChunk, ctx.groupByBlockID, ctx.groupByBlockIDData, ctx.groupByTexture));
        return true;
    }
    
    private boolean handleSetExport(DynmapCommandSender sender, String[] args, ExportContext ctx, DynmapCore core) {
        if (args.length < 3) {
            sender.sendMessage(String.format("Bounds: <%s,%s,%s> - <%s,%s,%s> on world '%s'", val(ctx.xmin), val(ctx.ymin), val(ctx.zmin),
                    val(ctx.xmax), val(ctx.ymax), val(ctx.zmax), ctx.world));
            return true;
        }
        for (int i = 1; i < (args.length-1); i += 2) {
            try {
                if (args[i].equals("x0")) {
                    ctx.xmin = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("x1")) {
                    ctx.xmax = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("y0")) {
                    ctx.ymin = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("y1")) {
                    ctx.ymax = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("z0")) {
                    ctx.zmin = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("z1")) {
                    ctx.zmax = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("world")) {
                    DynmapWorld w = core.getWorld(args[i+1]);
                    if (w != null) {
                        ctx.world = args[i+1];
                    }
                    else {
                        sender.sendMessage("Invalid world '" + args[i+1] + "'");
                        return true;
                    }
                }
                else if (args[i].equals("shader")) {
                    HDShader s = MapManager.mapman.hdmapman.shaders.get(args[i+1]);
                    if (s == null) {
                        sender.sendMessage("Unknown shader '" + args[i+1] + "'");
                        return true;
                    }
                    ctx.shader = args[i+1];
                }
                else if (args[i].equals("byChunk")) {
                    ctx.groupByChunk = args[i+1].equalsIgnoreCase("true");
                }
                else if (args[i].equals("byBlockID")) {
                    ctx.groupByBlockID = args[i+1].equalsIgnoreCase("true");
                }
                else if (args[i].equals("byBlockIDData")) {
                    ctx.groupByBlockIDData = args[i+1].equalsIgnoreCase("true");
                }
                else if (args[i].equals("byTexture")) {
                    ctx.groupByTexture = args[i+1].equalsIgnoreCase("true");
                }
                else {  // Unknown setting
                    sender.sendMessage("Unknown setting '" + args[i] + "'");
                    return true;
                }
            } catch (NumberFormatException nfx) {
                sender.sendMessage("Invalid value for '" + args[i] + "' - " + args[i+1]);
                return true;
            }
        }
        return handleInfo(sender, args, ctx, core);
    }

    private boolean handleRadius(DynmapCommandSender sender, String[] args, ExportContext ctx, DynmapCore core) {
        if ((sender instanceof DynmapPlayer) == false) {    // Not a player
            sender.sendMessage("Only usable by player");
            return true;
        }
        DynmapPlayer plyr = (DynmapPlayer) sender;
        DynmapLocation loc = plyr.getLocation();
        DynmapWorld world = null;
        if (loc != null) {
            world = core.getWorld(loc.world);
        }
        if (world == null) {
            sender.sendMessage("Location not found for player");
            return true;
        }
        int radius = 16;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
                if (radius < 0) {
                    sender.sendMessage("Invalid radius - " + args[1]);
                    return true;
                }
            } catch (NumberFormatException nfx) {
                sender.sendMessage("Invalid radius - " + args[1]);
                return true;
            }
        }
        ctx.xmin = (int)Math.floor(loc.x) - radius;
        ctx.xmax = (int)Math.ceil(loc.x) + radius;
        ctx.zmin = (int)Math.floor(loc.z) - radius;
        ctx.zmax = (int)Math.ceil(loc.z) + radius;
        ctx.ymin = 0;
        ctx.ymax = world.worldheight - 1;
        ctx.world = world.getName();
        return handleInfo(sender, args, ctx, core);
    }

    private boolean handlePosN(DynmapCommandSender sender, String[] args, ExportContext ctx, DynmapCore core, int n) {
        if ((sender instanceof DynmapPlayer) == false) {    // Not a player
            sender.sendMessage("Only usable by player");
            return true;
        }
        DynmapPlayer plyr = (DynmapPlayer) sender;
        DynmapLocation loc = plyr.getLocation();
        DynmapWorld world = null;
        if (loc != null) {
            world = core.getWorld(loc.world);
        }
        if (world == null) {
            sender.sendMessage("Location not found for player");
            return true;
        }
        if (n == 0) {
            ctx.xmin = (int)Math.floor(loc.x);
            ctx.ymin = (int)Math.floor(loc.y);
            ctx.zmin = (int)Math.floor(loc.z);
        }
        else {
            ctx.xmax = (int)Math.floor(loc.x);
            ctx.ymax = (int)Math.floor(loc.y);
            ctx.zmax = (int)Math.floor(loc.z);
        }
        ctx.world = world.getName();
        
        return handleInfo(sender, args, ctx, core);
    }
    
    private boolean handleDoExport(DynmapCommandSender sender, String[] args, ExportContext ctx, DynmapCore core) {
        if ((ctx.world == null) || (ctx.xmin == Integer.MIN_VALUE) || (ctx.ymin == Integer.MIN_VALUE) || 
            (ctx.zmin == Integer.MIN_VALUE) || (ctx.xmax == Integer.MIN_VALUE) || (ctx.ymax == Integer.MIN_VALUE) ||
            (ctx.zmax == Integer.MIN_VALUE)) {
            sender.sendMessage("Bounds not set");
            return true;
        }
        DynmapWorld w = core.getWorld(ctx.world);
        if (w == null) {
            sender.sendMessage("Invalid world - " + ctx.world);
            return true;
        }
        HDShader s = MapManager.mapman.hdmapman.shaders.get(ctx.shader);
        if (s == null) {
            sender.sendMessage("Invalid shader - " + ctx.shader);
            return true;
        }
        
        String basename = "dynmapexp";
        if (args.length > 1) {
            basename = args[1];
        }
        basename = basename.replace('/', '_');
        basename = basename.replace('\\', '_');
        File f = new File(core.getExportFolder(), basename + ".zip");
        int idx = 0;
        String finalBasename = basename;
        while (f.exists()) {
            idx++;
            finalBasename = basename + "_" + idx;
            f = new File(core.getExportFolder(), finalBasename + ".zip");
        }
        sender.sendMessage("Exporting to " + f.getPath());
        
        OBJExport exp = new OBJExport(f, s, w, core, finalBasename);
        exp.setRenderBounds(ctx.xmin, ctx.ymin, ctx.zmin, ctx.xmax, ctx.ymax, ctx.zmax);
        exp.setGroupEnabled(OBJExport.GROUP_CHUNK, ctx.groupByChunk);
        exp.setGroupEnabled(OBJExport.GROUP_TEXTURE, ctx.groupByTexture);
        exp.setGroupEnabled(OBJExport.GROUP_BLOCKID, ctx.groupByBlockID);
        exp.setGroupEnabled(OBJExport.GROUP_BLOCKIDMETA, ctx.groupByBlockIDData);
        MapManager.mapman.startOBJExport(exp, sender);
        
        return true;
    }
    
    private boolean handleResetExport(DynmapCommandSender sender, String[] args, ExportContext ctx, DynmapCore core) {
        sessions.remove(getContextID(sender));
        return true;
    }
    
    private boolean handlePurgeExport(DynmapCommandSender sender, String[] args, ExportContext ctx, DynmapCore core) {
        if (args.length > 1) {
            String basename = args[1];
            basename = basename.replace('/', '_');
            basename = basename.replace('\\', '_');
            File f = new File(core.getExportFolder(), basename + ".zip");
            if (f.exists()) {
                f.delete();
                sender.sendMessage("Removed " + f.getPath());
            }
            else {
                sender.sendMessage(f.getPath() + " not found");
            }
        }
        return true;
    }
    
    private String val(int v) {
        if (v == Integer.MIN_VALUE)
            return "N/A";
        else
            return Integer.toString(v);
    }
}
