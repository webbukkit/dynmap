package org.dynmap;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

/**
 * Custom bukkit event, corresponding to the receiving of a web-chat message from a web UI user
 */
public class DynmapWebChatEvent extends Event implements Cancellable {
    private String source;
    private String name;
    private String message;
    private boolean cancelled;
    
    public DynmapWebChatEvent(String source, String name, String message) {
        super("org.dynmap.DynmapWebChatEvent");
        this.source = source;
        this.name = name;
        this.message = message;
        this.cancelled = false;
    }
    public boolean isCancelled() { return cancelled; }
    
    public void setCancelled(boolean cancel) { cancelled = cancel; }

    public String getSource() { return source; }
    
    public String getName() { return name; }
    
    public String getMessage() { return message; }
    
}
