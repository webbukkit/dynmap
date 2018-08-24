package org.dynmap;

public class ChatEvent {
    public String source;
    public String name;
    public String message;
    public ChatEvent(String source, String name, String message) {
        this.source = source;
        this.name = name;
        this.message = message;
    }
}
