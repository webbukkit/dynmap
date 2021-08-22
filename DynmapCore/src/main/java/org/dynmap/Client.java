package org.dynmap;

import java.io.IOException;
import java.io.Writer;
import java.util.Random;

import org.json.simple.JSONAware;
import org.json.simple.JSONStreamAware;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.dynmap.common.DynmapChatColor;

public class Client {
    
    public static class Update implements JSONAware, JSONStreamAware {
        public long timestamp = System.currentTimeMillis();

        @Override
        public String toJSONString() {
            return org.dynmap.web.Json.stringifyJson(this);
        }

        @Override
        public void writeJSONString(Writer w) throws IOException {
            w.write(toJSONString());
        }
    }

    public static class ChatMessage extends Update {
        public String type = "chat";
        public String source;
        public String playerName;   // Note: this needs to be client-safe HTML text (can include tags, but only sanitized ones)
        public String message;
        public String account;
        public String channel;
        public ChatMessage(String source, String channel, String playerName, String message, String playeraccount) {
            this.source = source;
            if (ClientUpdateComponent.hideNames)
                this.playerName = "";
            else if (ClientUpdateComponent.usePlayerColors)
                this.playerName = Client.encodeColorInHTML(playerName);
            else
                this.playerName = Client.stripColor(playerName);
            this.message = DynmapChatColor.stripColor(message);
            this.account = playeraccount;
            this.channel = channel;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof ChatMessage) {
                ChatMessage m = (ChatMessage)o;
                return m.source.equals(source) && m.playerName.equals(playerName) && m.message.equals(message);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return source.hashCode() ^ playerName.hashCode() ^ message.hashCode();
        }
    }

    public static class PlayerJoinMessage extends Update {
        public String type = "playerjoin";
        public String playerName;   // Note: this needs to be client-safe HTML text (can include tags, but only sanitized ones)
        public String account;
        public PlayerJoinMessage(String playerName, String playeraccount) {
            if (ClientUpdateComponent.hideNames)
                this.playerName = "";
            else if (ClientUpdateComponent.usePlayerColors)
                this.playerName = Client.encodeColorInHTML(playerName);
            else
                this.playerName = Client.stripColor(playerName);
            this.account = playeraccount;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof PlayerJoinMessage) {
                PlayerJoinMessage m = (PlayerJoinMessage)o;
                return m.playerName.equals(playerName);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return account.hashCode();
        }
    }

    public static class PlayerQuitMessage extends Update {
        public String type = "playerquit";
        public String playerName;   // Note: this needs to be client-safe HTML text (can include tags, but only sanitized ones)
        public String account;
        public PlayerQuitMessage(String playerName, String playeraccount) {
            if (ClientUpdateComponent.hideNames)
                this.playerName = "";
            else if (ClientUpdateComponent.usePlayerColors)
                this.playerName = Client.encodeColorInHTML(playerName);
            else
                this.playerName = Client.stripColor(playerName);
            this.account = playeraccount;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof PlayerQuitMessage) {
                PlayerQuitMessage m = (PlayerQuitMessage)o;
                return m.playerName.equals(playerName);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return account.hashCode();
        }
    }

    public static class Tile extends Update {
        public String type = "tile";
        public String name;

        public Tile(String name) {
            this.name = name;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof Tile) {
                Tile m = (Tile)o;
                return m.name.equals(name);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static class DayNight extends Update {
        public String type = "daynight";
        public boolean isday;

        public DayNight(boolean isday) {
            this.isday = isday;
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof DayNight) {
                return true;
            }
            return false;
        }
        @Override
        public int hashCode() {
            return 12345;
        }
    }

    public static class ComponentMessage extends Update {
        public String type = "component";
        /* Each subclass must provide 'ctype' string for component 'type' */
    }
    
    // Strip color - assume we're returning safe html text
    public static String stripColor(String s) {
        s = DynmapChatColor.stripColor(s);    /* Strip standard color encoding */
        /* Handle Essentials nickname encoding too */
        int idx = 0;
        while((idx = s.indexOf('&', idx)) >= 0) {
            char c = s.charAt(idx+1);   /* Get next character */
            if(c == '&') {  /* Another ampersand */
                s = s.substring(0, idx) + s.substring(idx+1);
            }
            else {
                s = s.substring(0, idx) + s.substring(idx+2);
            }
            idx++;
        }
        // Apply sanitize policy before returning
        return sanitizeHTML(s);
    }
    private static String[][] codes = {
        { "0", "<span style=\'color:#000000\'>" },
        { "1", "<span style=\'color:#0000AA\'>" },
        { "2", "<span style=\'color:#00AA00\'>" },
        { "3", "<span style=\'color:#00AAAA\'>" },
        { "4", "<span style=\'color:#AA0000\'>" },
        { "5", "<span style=\'color:#AA00AA\'>" },
        { "6", "<span style=\'color:#FFAA00\'>" },
        { "7", "<span style=\'color:#AAAAAA\'>" },
        { "8", "<span style=\'color:#555555\'>" },
        { "9", "<span style=\'color:#5555FF\'>" },
        { "a", "<span style=\'color:#55FF55\'>" },
        { "b", "<span style=\'color:#55FFFF\'>" },
        { "c", "<span style=\'color:#FF5555\'>" },
        { "d", "<span style=\'color:#FF55FF\'>" },
        { "e", "<span style=\'color:#FFFF55\'>" },
        { "f", "<span style=\'color:#FFFFFF\'>" },
        { "l", "<span style=\'font-weight:bold\'>" },
        { "m", "<span style=\'text-decoration:line-through\'>" },
        { "n", "<span style=\'text-decoration:underline\'>" },
        { "o", "<span style=\'font-style:italic\'>" },
        { "r", "<span style=\'font-style:normal,text-decoration:none,font-weight:normal\'>" }
    };
    private static Random rnd = new Random();
    private static String rndchars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    // Replace color codes with corresponding <span - assume we're returning safe HTML text 
    public static String encodeColorInHTML(String s) {
        StringBuilder sb = new StringBuilder();
        int cnt = s.length();
        int spancnt = 0;
        boolean magic = false;
        for (int i = 0; i < cnt; i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') { // Escape? 
                i++;    // Move past it
                c = s.charAt(i);
                if (c == 'k') { // Magic text?
                    magic = true;
                }
                else if (c == 'r') { // reset
                    magic = false;
                }
                for (int j = 0; j < codes.length; j++) {
                    if (codes[j][0].charAt(0) == c) {   // Matching code?
                        sb.append(codes[j][1]); // Substitute
                        spancnt++;
                        break;
                    }
                    else if (c == 'x') { // Essentials nickname hexcode format
                        if (i + 12 <= cnt){ // Check if string is at least long enough to be valid hexcode
                            if (s.charAt(i+1) == s.charAt(i+3) &&
                                s.charAt(i+1) == s.charAt(i+5) &&
                                s.charAt(i+1) == s.charAt(i+7) &&
                                s.charAt(i+1) == s.charAt(i+9) &&
                                s.charAt(i+1) == s.charAt(i+11) && // Check if there are enough \u00A7 in a row
                                s.charAt(i+1) == '\u00A7'){ 
                                    StringBuilder hex = new StringBuilder().append(s.charAt(i+2))
                                                                            .append(s.charAt(i+4))
                                                                            .append(s.charAt(i+6))
                                                                            .append(s.charAt(i+8))
                                                                            .append(s.charAt(i+10))
                                                                            .append(s.charAt(i+12)); // Build hexcode string
                                    sb.append("<span style=\'color:#" + hex + "\'>"); // Substitute with hexcode
                                i = i + 12; //move past hex codes
                            }
                        }
                        break;
                    }
                }
            }
            else if (c == '&') {    // Essentials color code?
                i++;    // Move past it
                c = s.charAt(i);
                if (c == '&') { // Amp?
                    sb.append(c);
                }
                else {
                    if (c == 'k') { // Magic text?
                        magic = true;
                    }
                    else if (c == 'r') { // reset
                        magic = false;
                    }
                    for (int j = 0; j < codes.length; j++) {
                        if (codes[j][0].charAt(0) == c) {   // Matching code?
                            sb.append(codes[j][1]); // Substitute
                            spancnt++;
                            break;
                        }
                    }
                }
            }
            else if (magic) {
                sb.append(rndchars.charAt(rnd.nextInt(rndchars.length())));
            }
            else {
                sb.append(c);
            }
        }
        for (int i = 0; i < spancnt; i++) {
            sb.append("</span>");
        }
        return sanitizeHTML(sb.toString());
    }

    private static PolicyFactory sanitizer = null; 
    public static String sanitizeHTML(String html) {
        PolicyFactory s = sanitizer;
        if (s == null) {
            // Generous but safe html formatting allowances
            s = Sanitizers.FORMATTING.and(Sanitizers.BLOCKS).and(Sanitizers.IMAGES).and(Sanitizers.LINKS).and(Sanitizers.STYLES);
            sanitizer = s;
        }
        return sanitizer.sanitize(html);
    }
}
