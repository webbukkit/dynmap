package org.dynmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.hdmap.HDLighting;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.HDShader;
import org.dynmap.utils.MapChunkCache.HiddenChunkStyle;
import org.dynmap.utils.RectangleVisibilityLimit;
import org.dynmap.utils.RoundVisibilityLimit;
import org.dynmap.utils.VisibilityLimit;

/**
 * Handler for world and map edit commands (via /dmap)
 */
public class DynmapMapCommands {

    private boolean checkIfActive(DynmapCore core, DynmapCommandSender sender) {
        if ((!core.getPauseFullRadiusRenders()) || (!core.getPauseUpdateRenders())) {
            sender.sendMessage("Cannot edit map data while rendering active - run '/dynmap pause all' to pause rendering");
            return true;
        }
        return false;
    }
    
    public boolean processCommand(DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapCore core) {
        /* Re-parse args - handle doublequotes */
        args = DynmapCore.parseArgs(args, sender);
        if (args.length < 1)
            return false;
        cmd = args[0];
        boolean rslt = false;
        boolean edit = false;
        
        if (cmd.equalsIgnoreCase("worldlist")) {
            rslt = handleWorldList(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("perspectivelist")) {
            rslt = handlePerspectiveList(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("shaderlist")) {
            rslt = handleShaderList(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("lightinglist")) {
            rslt = handleLightingList(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("maplist")) {
            rslt = handleMapList(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("blocklist")) {
            rslt = handleBlockList(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("worldgetlimits")) {
        	rslt = handleWorldGetLimits(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("worldremovelimit")) {
        	edit = true;
        	rslt = handleWorldRemoveLimit(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("worldaddlimit")) {
        	edit = true;
        	rslt = handleWorldAddLimit(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("worldset")) {
        	edit = true;
    		rslt = handleWorldSet(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("mapdelete")) {
        	edit = true;
            rslt = handleMapDelete(sender, args, core);
    	}
        else if (cmd.equalsIgnoreCase("worldreset")) {
        	edit = true;
            rslt = handleWorldReset(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("mapset")) {
        	edit = true;
            rslt = handleMapSet(sender, args, core, false);
        }
        else if (cmd.equalsIgnoreCase("mapadd")) {
        	edit = true;
            rslt = handleMapSet(sender, args, core, true);
        }
        if (edit && rslt) {
            sender.sendMessage("If you are done editing map data, run '/dynmap pause none' to resume rendering");
        }
        return rslt;
    }
    
    private boolean handleWorldList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldlist"))
            return true;
        Set<String> wnames = null;
        if(args.length > 1) {
            wnames = new HashSet<String>();
            for(int i = 1; i < args.length; i++)
                wnames.add(DynmapWorld.normalizeWorldName(args[i]));
        }
        /* Get active worlds */
        for(DynmapWorld w : core.getMapManager().getWorlds()) {
            if((wnames != null) && (wnames.contains(w.getName()) == false)) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("world ").append(w.getName()).append(": loaded=").append(w.isLoaded()).append(", enabled=").append(w.isEnabled());
            sb.append(", title=").append(w.getTitle());
            
            DynmapLocation loc = w.getCenterLocation();
            if(loc != null) {
                sb.append(", center=").append(loc.x).append("/").append(loc.y).append("/").append(loc.z);
            }
            sb.append(", extrazoomout=").append(w.getExtraZoomOutLevels()).append(", sendhealth=").append(w.sendhealth);
            sb.append(", sendposition=").append(w.sendposition);
            sb.append(", protected=").append(w.is_protected);
            sb.append(", showborder=").append(w.showborder);
            if(w.tileupdatedelay > 0) {
                sb.append(", tileupdatedelay=").append(w.tileupdatedelay);
            }
            sender.sendMessage(sb.toString());
        }
        /* Get disabled worlds */
        for(String wn : core.getMapManager().getDisabledWorlds()) {
            if((wnames != null) && (wnames.contains(wn) == false)) {
                continue;
            }
            sender.sendMessage("world " + wn + ": isenabled=false");
        }
        
        return true;
    }

    private boolean handleWorldGetLimits(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldlist"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World ID required");
            return true;
        }
        String world_id = args[1];
        DynmapWorld w = core.getMapManager().getWorld(world_id);
        if (w == null) {
            sender.sendMessage(String.format("World %s not found", world_id));
            return true;
        }
        sender.sendMessage("limits:");
        int viscnt = 0;
        if ((w.visibility_limits != null) && (w.visibility_limits.size() > 0)) {
        	viscnt = w.visibility_limits.size();
        	for (int i = 0; i < viscnt; i++) {
        		VisibilityLimit limit = w.visibility_limits.get(i);
        		if (limit instanceof RoundVisibilityLimit) {
        			RoundVisibilityLimit rlimit = (RoundVisibilityLimit) limit;
        			sender.sendMessage(String.format(" %d: limittype=visible, type=round, center=%d/%d, radius=%f", i, rlimit.x_center, rlimit.z_center, rlimit.radius));
        		}
        		else if (limit instanceof RectangleVisibilityLimit) {
        			RectangleVisibilityLimit rlimit = (RectangleVisibilityLimit) limit;
        			sender.sendMessage(String.format(" %d: limittype=visible, type=rect, corner1=%d/%d, corner2=%d/%d", i, rlimit.x_min, rlimit.z_min, rlimit.x_max, rlimit.z_max));
        		}
        	}
        }
        if ((w.hidden_limits != null) && (w.hidden_limits.size() > 0)) {
        	for (int i = 0; i < w.hidden_limits.size(); i++) {
        		VisibilityLimit limit = w.hidden_limits.get(i);
        		if (limit instanceof RoundVisibilityLimit) {
        			RoundVisibilityLimit rlimit = (RoundVisibilityLimit) limit;
        			sender.sendMessage(String.format(" %d: limittype=hidden, type=round, center=%d/%d, radius=%f", i + viscnt, rlimit.x_center, rlimit.z_center, rlimit.radius));
        		}
        		else if (limit instanceof RectangleVisibilityLimit) {
        			RectangleVisibilityLimit rlimit = (RectangleVisibilityLimit) limit;
        			sender.sendMessage(String.format(" %d: limittype=hidden, type=rect, corner1=%d/%d, corner2=%d/%d", i + viscnt, rlimit.x_min, rlimit.z_min, rlimit.x_max, rlimit.z_max));
        		}
        	}
        }
        sender.sendMessage("hiddenstyle: " + w.hiddenchunkstyle.getValue());
        return true;
    }

    private boolean handleWorldAddLimit(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World ID required");
            return true;
        }
        /* Test if render active - quit if so */
        if (checkIfActive(core, sender)) {
            return true;
        }
        String world_id = args[1];
        DynmapWorld w = core.getMapManager().getWorld(world_id);
        if (w == null) {
            sender.sendMessage(String.format("World %s not found", world_id));
            return true;
        }
        String limittype = "visible";
        String type = "rect";	// Default to rectangle
        int corner1[] = null;
        int corner2[] = null;
        int center[] = null;
        double radius = 0.0;
        HiddenChunkStyle style = null;
        // Other args are field:value
        for (int argid = 2; argid < args.length; argid++) {
        	String[] argval = args[argid].split(":");
        	if (argval.length != 2) {
        		sender.sendMessage("Argument witout value: " + args[argid]);
        		return false;
        	}
        	String[] toks;
        	String id = argval[0];
        	String val = argval[1];
        	switch (id) {
        		case "type":
        			if ((val.equals("round")) || (val.equals("rect"))) {
        				type = val;
        			}
        			else {
                		sender.sendMessage("Bad type value: " + val);
            			return false;
        			}
        			break;
        		case "limittype":
        			if ((val.equals("visible")) || (val.equals("hidden"))) {
        				limittype = val;
        			}
        			else {
                		sender.sendMessage("Bad limittype value: " + val);
            			return false;
        			}
        			break;
        		case "corner1":
        		case "corner2":
        		case "center":
        			if ((type.equals("rect") && id.equals("center")) ||
    					(type.equals("round") && (!id.equals("center")))) {
                		sender.sendMessage("Bad parameter for type " + type + ": " + id);
            			return false;        				
        			}
                    toks = val.split("/");
                    if (toks.length == 2) {
                        int x = 0, z = 0;
                        x = Integer.valueOf(toks[0]);
                        z = Integer.valueOf(toks[1]);
                        switch (id) {
                        	case "corner1":
                        		corner1 = new int[] { x, z };
                        		break;
                        	case "corner2":
                        		corner2 = new int[] { x, z };
                        		break;
                        	case "center":
                        		center = new int[] { x, z };
                        		break;
                    	}
                    }
                    else {
                		sender.sendMessage("Bad value for parameter " + id + ": " + val);
            			return false;     
                    }
                    break;
        		case "radius":
        			if (!type.equals("round")) {
                		sender.sendMessage("Bad parameter for type " + type + ": " + id);
            			return false;        				
        			}
        			radius = Double.valueOf(val);
        			break;
        		case "style":
        			style = HiddenChunkStyle.fromValue(val);
        			if (style == null) {
                		sender.sendMessage("Bad parameter for style: " + val);
            			return false;        				
        			}
        			break;
        		default:
            		sender.sendMessage("Bad parameter: " + id);
            		return false;
        	}
        }
        // If enough for rectange area, add it
        VisibilityLimit newlimit = null;
        if ((type.contentEquals("rect") && (corner1 != null) && (corner2 != null))) {
        	newlimit = new RectangleVisibilityLimit(corner1[0], corner1[1], corner2[0], corner2[1]);      	
        }
        else if ((type.contentEquals("round") && (center != null) && (radius > 0.0))) {
        	newlimit = new RoundVisibilityLimit(center[0], center[1], radius);
        }
        boolean updated = false;
        if (newlimit != null) {
        	if (limittype.contentEquals("visible")) {
        		if (w.visibility_limits == null) { w.visibility_limits = new ArrayList<VisibilityLimit>(); }
        		w.visibility_limits.add(newlimit);
        	}
        	else {
        		if (w.hidden_limits == null) { w.hidden_limits = new ArrayList<VisibilityLimit>(); }
        		w.hidden_limits.add(newlimit);        		
        	}
            updated = true;
        }
        // If new style, apply it
        if (style != null) {
        	w.hiddenchunkstyle = style;
            updated = true;        	
        }
        // Apply update
        if (updated) {
            core.updateWorldConfig(w);
            sender.sendMessage("Refreshing configuration for world " + world_id);
            core.refreshWorld(world_id);
        }
        return true;
    }

    private boolean handleWorldRemoveLimit(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;
        if(args.length < 3) {
            sender.sendMessage("World ID and limit index required");
            return true;
        }
        /* Test if render active - quit if so */
        if (checkIfActive(core, sender)) {
            return true;
        }
        String world_id = args[1];
        DynmapWorld w = core.getMapManager().getWorld(world_id);
        if (w == null) {
            sender.sendMessage(String.format("World %s not found", world_id));
            return true;
        }
        boolean updated = false;
        String idx = args[2];
        int idxnum = Integer.valueOf(idx);
        int viscnt = (w.visibility_limits != null) ? w.visibility_limits.size() : 0;
        if ((idxnum >= 0) && (idxnum < viscnt)) {
        	w.visibility_limits.remove(idxnum);
        	if (w.visibility_limits.size() == 0) {
        		w.visibility_limits = null;
        	}
        	updated = true;
        }
        else {
        	idxnum -= viscnt;
        	if ((idxnum >= 0) && (w.hidden_limits != null) && (idxnum < w.hidden_limits.size())) {
            	w.hidden_limits.remove(idxnum);
            	if (w.hidden_limits.size() == 0) {
            		w.hidden_limits = null;
            	}
            	updated = true;	
        	}
        }
        // Apply update
        if (updated) {
            core.updateWorldConfig(w);
            sender.sendMessage("Refreshing configuration for world " + world_id);
            core.refreshWorld(world_id);
        }
        return true;
    }
    
    private boolean handleWorldSet(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;
        if(args.length < 3) {
            sender.sendMessage("World name and setting:newvalue required");
            return true;
        }
        String wname = args[1]; /* Get world name */
        /* Test if render active - quit if so */
        if (checkIfActive(core, sender)) {
            return true;
        }
        
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        
        boolean did_update = false;
        for(int i = 2; i < args.length; i++) {
            String[] tok = args[i].split(":");  /* Split at colon */
            if(tok.length != 2) {
                sender.sendMessage("Syntax error: " + args[i]);
                return false;
            }
            if(tok[0].equalsIgnoreCase("enabled")) {
                did_update |= core.setWorldEnable(wname, !tok[1].equalsIgnoreCase("false"));
            }
            else if(tok[0].equalsIgnoreCase("title")) {
                if(w == null) {
                    sender.sendMessage("Cannot set extrazoomout on disabled or undefined world");
                    return true;
                }
                w.setTitle(tok[1]);
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("sendposition")) {
                if(w == null) {
                    sender.sendMessage("Cannot set sendposition on disabled or undefined world");
                    return true;
                }
                w.sendposition = tok[1].equals("true");
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("sendhealth")) {
                if(w == null) {
                    sender.sendMessage("Cannot set sendhealth on disabled or undefined world");
                    return true;
                }
                w.sendhealth = tok[1].equals("true");
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("showborder")) {
                if(w == null) {
                    sender.sendMessage("Cannot set sendworldborder on disabled or undefined world");
                    return true;
                }
                w.showborder = tok[1].equals("true");
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("protected")) {
                if(w == null) {
                    sender.sendMessage("Cannot set protected on disabled or undefined world");
                    return true;
                }
                w.is_protected = tok[1].equals("true");
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("extrazoomout")) {  /* Extrazoomout setting */
                if(w == null) {
                    sender.sendMessage("Cannot set extrazoomout on disabled or undefined world");
                    return true;
                }
                int exo = -1;
                try {
                    exo = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                if((exo < 0) || (exo > 32)) {
                    sender.sendMessage("Invalid value for extrazoomout: " + tok[1]);
                    return true;
                }
                did_update |= core.setWorldZoomOut(wname, exo);
            }
            else if(tok[0].equalsIgnoreCase("tileupdatedelay")) {  /* tileupdatedelay setting */
                if(w == null) {
                    sender.sendMessage("Cannot set tileupdatedelay on disabled or undefined world");
                    return true;
                }
                int tud = -1;
                try {
                    tud = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                did_update |= core.setWorldTileUpdateDelay(wname, tud);
            }
            else if(tok[0].equalsIgnoreCase("center")) {    /* Center */
                if(w == null) {
                    sender.sendMessage("Cannot set center on disabled or undefined world");
                    return true;
                }
                boolean good = false;
                DynmapLocation loc = null;
                try {
                    String[] toks = tok[1].split("/");
                    if(toks.length == 3) {
                        double x = 0, y = 0, z = 0;
                        x = Double.valueOf(toks[0]);
                        y = Double.valueOf(toks[1]);
                        z = Double.valueOf(toks[2]);
                        loc = new DynmapLocation(wname, x, y, z);
                       good = true;
                    }
                    else if(tok[1].equalsIgnoreCase("default")) {
                        good = true;
                    }
                    else if(tok[1].equalsIgnoreCase("here")) {
                        if(sender instanceof DynmapPlayer) {
                            loc = ((DynmapPlayer)sender).getLocation();
                            good = true;
                        }
                        else {
                            sender.sendMessage("Setting center to 'here' requires player");
                            return true;
                        }
                    }
                } catch (NumberFormatException nfx) {}
                if(!good) {
                    sender.sendMessage("Center value must be formatted x/y/z or be set to 'default' or 'here'");
                    return true;
                }
                did_update |= core.setWorldCenter(wname, loc);
            }
            else if(tok[0].equalsIgnoreCase("order")) {
                if(w == null) {
                    sender.sendMessage("Cannot set order on disabled or undefined world");
                    return true;
                }
                int order = -1;
                try {
                    order = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                if(order < 1) {
                    sender.sendMessage("Order value must be number from 1 to number of worlds");
                    return true;
                }
                did_update |= core.setWorldOrder(wname, order-1);
            }
        }
        /* If world updatd, refresh it */
        if(did_update) {
            sender.sendMessage("Refreshing configuration for world " + wname);
            core.refreshWorld(wname);
        }
        
        return true;
    }
    
    private boolean handleMapList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.maplist"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World name is required");
            return true;
        }
        String wname = args[1]; /* Get world name */
        
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        if(w == null) { 
            sender.sendMessage("Only loaded world can be listed");
            return true;
        }
        List<MapType> maps = w.maps;
        for(MapType mt : maps) {
            if(mt instanceof HDMap) {
                HDMap hdmt = (HDMap)mt;
                StringBuilder sb = new StringBuilder();
                sb.append("map ").append(mt.getName()).append(": prefix=").append(hdmt.getPrefix()).append(", title=").append(hdmt.getTitle());
                sb.append(", perspective=").append(hdmt.getPerspective().getName()).append(", shader=").append(hdmt.getShader().getName());
                sb.append(", lighting=").append(hdmt.getLighting().getName()).append(", mapzoomin=").append(hdmt.getMapZoomIn()).append(", mapzoomout=").append(hdmt.getMapZoomOutLevels());
                sb.append(", img-format=").append(hdmt.getImageFormatSetting()).append(", icon=").append(hdmt.getIcon());
                sb.append(", append-to-world=").append(hdmt.getAppendToWorld()).append(", boostzoom=").append(hdmt.getBoostZoom());
                sb.append(", protected=").append(hdmt.isProtected());
                if(hdmt.tileupdatedelay > 0) {
                    sb.append(", tileupdatedelay=").append(hdmt.tileupdatedelay);
                }
                sender.sendMessage(sb.toString());
            }
        }
        
        return true;
    }

    private boolean handleMapDelete(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.mapdelete"))
            return true;
        if (checkIfActive(core, sender)) {
        	return false;
        }
        if(args.length < 2) {
            sender.sendMessage("World:map name required");
            return true;
        }
        for(int i = 1; i < args.length; i++) {
            String world_map_name = args[i];
            String[] tok = world_map_name.split(":");
            if(tok.length != 2) {
                sender.sendMessage("Invalid world:map name: " + world_map_name);
                return true;
            }
            String wname = tok[0];
            String mname = tok[1];
            DynmapWorld w = core.getWorld(wname);   /* Try to get world */
            if(w == null) {
                sender.sendMessage("Cannot delete maps from disabled or unloaded world: " + wname);
                return true;
            }
            List<MapType> maps = new ArrayList<MapType>(w.maps);
            boolean done = false;
            for(int idx = 0; (!done) && (idx < maps.size()); idx++) {
                MapType mt = maps.get(idx);
                if(mt.getName().equals(mname)) {
                    w.maps.remove(mt);
                    done = true;
                }
            }
            /* If done, save updated config for world */
            if(done) {
                if(core.updateWorldConfig(w)) {
                    sender.sendMessage("Refreshing configuration for world " + wname);
                    core.refreshWorld(wname);
                }
            }
        }
        
        return true;
    }
    
    private boolean handleWorldReset(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldreset"))
            return true;
        if (checkIfActive(core, sender)) {
        	return false;
        }
        if(args.length < 2) {
            sender.sendMessage("World name required");
            return true;
        }
        String wname = args[1]; /* Get world name */
        
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        /* If not loaded, cannot reset */
        if(w == null) {
            sender.sendMessage("Cannot reset world that is not loaded or enabled");
            return true;
        }
        ConfigurationNode cn = null;
        if(args.length > 2) {
            cn = core.getTemplateConfigurationNode(args[2]);
        }
        else {  /* Else get default */
            cn = core.getDefaultTemplateConfigurationNode(w);
        }
        if(cn == null) {
            sender.sendMessage("Cannot load template");
            return true;
        }
        ConfigurationNode cfg = w.saveConfiguration();  /* Get configuration */
        cfg.extend(cn);    /* And apply template */

        /* And set world config */
        if(core.replaceWorldConfig(wname, cfg)) {
            sender.sendMessage("Reset configuration for world " + wname);
            core.refreshWorld(wname);
        }
        
        return true;
    }
    
    private boolean handleMapSet(DynmapCommandSender sender, String[] args, DynmapCore core, boolean isnew) {
        if(!core.checkPlayerPermission(sender, isnew?"dmap.mapadd":"dmap.mapset"))
            return true;
        if (checkIfActive(core, sender)) {
        	return false;
        }
        if(args.length < 2) {
            sender.sendMessage("World:map name required");
            return true;
        }
        String world_map_name = args[1];
        String[] tok = world_map_name.split(":");
        if(tok.length != 2) {
            sender.sendMessage("Invalid world:map name: " + world_map_name);
            return true;
        }
        String wname = tok[0];
        String mname = tok[1];

        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        if(w == null) {
            sender.sendMessage("Cannot update maps from disabled or unloaded world: " + wname);
            return true;
        }
        HDMap mt = null;
        /* Find the map */
        for(MapType map : w.maps) {
            if(map instanceof HDMap) {
                if(map.getName().equals(mname)) {
                    mt = (HDMap)map;
                    break;
                }
            }
        }
        /* If new, make default map instance */
        if(isnew) {
            if(mt != null) {
                sender.sendMessage("Map " + mname + " already exists on world " + wname);
                return true;
            }
            ConfigurationNode cn = new ConfigurationNode();
            cn.put("name", mname);
            mt = new HDMap(core, cn);
            if(mt.getName() != null) {
                w.maps.add(mt); /* Add to end, by default */
            }
            else {
                sender.sendMessage("Map " + mname + " not valid");
                return true;
            }
        }
        else {
            if(mt == null) {
                sender.sendMessage("Map " + mname + " not found on world " + wname);
                return true;
            }
        }
        boolean did_update = isnew;
        for(int i = 2; i < args.length; i++) {
            tok = args[i].split(":", 2);  /* Split at colon */
            if(tok.length < 2) {
                String[] newtok = new String[2];
                newtok[0] = tok[0];
                newtok[1] = "";
                tok = newtok;
            }
            if(tok[0].equalsIgnoreCase("prefix")) {
                /* Check to make sure prefix is unique */
                for(MapType map : w.maps){
                    if(map == mt) continue;
                    if(map instanceof HDMap) {
                        if(((HDMap)map).getPrefix().equals(tok[1])) {
                            sender.sendMessage("Prefix " + tok[1] + " already in use");
                            return true;
                        }
                    }
                }
                did_update |= mt.setPrefix(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("title")) {
                did_update |= mt.setTitle(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("icon")) {
                did_update |= mt.setIcon(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("mapzoomin")) {
                int mzi = -1;
                try {
                    mzi = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if((mzi < 0) || (mzi > 32)) {
                    sender.sendMessage("Invalid mapzoomin value: " + tok[1]);
                    return true;
                }
                did_update |= mt.setMapZoomIn(mzi);
            }
            else if(tok[0].equalsIgnoreCase("mapzoomout")) {
                int mzi = -1;
                try {
                    mzi = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if((mzi < 0) || (mzi > 32)) {
                    sender.sendMessage("Invalid mapzoomout value: " + tok[1]);
                    return true;
                }
                did_update |= mt.setMapZoomOut(mzi);
            }
            else if(tok[0].equalsIgnoreCase("boostzoom")) {
                int mzi = -1;
                try {
                    mzi = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if((mzi < 0) || (mzi > 3)) {
                    sender.sendMessage("Invalid boostzoom value: " + tok[1]);
                    return true;
                }
                did_update |= mt.setBoostZoom(mzi);
            }
            else if(tok[0].equalsIgnoreCase("tileupdatedelay")) {
                int tud = -1;
                try {
                    tud = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                did_update |= mt.setTileUpdateDelay(tud);
            }
            else if(tok[0].equalsIgnoreCase("perspective")) {
                if(MapManager.mapman != null) {
                    HDPerspective p = MapManager.mapman.hdmapman.perspectives.get(tok[1]);
                    if(p == null) {
                        sender.sendMessage("Perspective not found: " + tok[1]);
                        return true;
                    }
                    did_update |= mt.setPerspective(p);
                }
            }
            else if(tok[0].equalsIgnoreCase("shader")) {
                if(MapManager.mapman != null) {
                    HDShader s = MapManager.mapman.hdmapman.shaders.get(tok[1]);
                    if(s == null) {
                        sender.sendMessage("Shader not found: " + tok[1]);
                        return true;
                    }
                    did_update |= mt.setShader(s);
                }
            }
            else if(tok[0].equalsIgnoreCase("lighting")) {
                if(MapManager.mapman != null) {
                    HDLighting l = MapManager.mapman.hdmapman.lightings.get(tok[1]);
                    if(l == null) {
                        sender.sendMessage("Lighting not found: " + tok[1]);
                        return true;
                    }
                    did_update |= mt.setLighting(l);
                }
            }
            else if(tok[0].equalsIgnoreCase("img-format")) {
                if((!tok[1].equals("default")) && (MapType.ImageFormat.fromID(tok[1]) == null)) {
                    sender.sendMessage("Image format not found: " + tok[1]);
                    return true;
                }
                did_update |= mt.setImageFormatSetting(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("order")) {
                int idx = -1;
                try {
                    idx = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if(idx < 1) {
                    sender.sendMessage("Invalid order position: " + tok[1]);
                    return true;
                }
                idx--;
                /* Remove and insert at position */
                w.maps.remove(mt);
                if(idx < w.maps.size())
                    w.maps.add(idx, mt);
                else
                    w.maps.add(mt);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("append-to-world")) {
                did_update |= mt.setAppendToWorld(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("protected")) {
                did_update |= mt.setProtected(Boolean.parseBoolean(tok[1]));
            }
        }
        if(did_update) {
            if(core.updateWorldConfig(w)) {
                sender.sendMessage("Refreshing configuration for world " + wname);
                core.refreshWorld(wname);
            }
        }

        return true;
    }
    
    private boolean handlePerspectiveList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.perspectivelist"))
            return true;
        if(MapManager.mapman != null) {
            StringBuilder sb = new StringBuilder();
            for(HDPerspective p : MapManager.mapman.hdmapman.perspectives.values()) {
                sb.append(p.getName()).append(' ');
            }
            sender.sendMessage(sb.toString());
        }
        return true;
    }

    private boolean handleShaderList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.shaderlist"))
            return true;
        if(MapManager.mapman != null) {
            StringBuilder sb = new StringBuilder();
            for(HDShader p : MapManager.mapman.hdmapman.shaders.values()) {
                sb.append(p.getName()).append(' ');
            }
            sender.sendMessage(sb.toString());
        }
        return true;
    }

    private boolean handleLightingList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.lightinglist"))
            return true;
        if(MapManager.mapman != null) {
            StringBuilder sb = new StringBuilder();
            for(HDLighting p : MapManager.mapman.hdmapman.lightings.values()) {
                sb.append(p.getName()).append(' ');
            }
            sender.sendMessage(sb.toString());
        }
        return true;
    }

    private boolean handleBlockList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.blklist"))
            return true;
        Map<String, Integer> map = core.getServer().getBlockUniqueIDMap();
        TreeSet<String> keys = new TreeSet<String>(map.keySet());
        for (String k : keys) {
            sender.sendMessage(k + ": " + map.get(k));
        }
        return true;
    }
}
