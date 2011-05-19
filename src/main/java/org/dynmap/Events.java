package org.dynmap;
import java.util.HashMap;
import java.util.Map;


public class Events {
    public Map<String, Event<?>> events = new HashMap<String, Event<?>>();
    public <T> void addListener(String eventName, Event.Listener<T> listener) {
        Event<?> genericEvent = events.get(eventName);
        Event<T> event = null;
        if (genericEvent != null) {
            event = (Event<T>)genericEvent;
        } else {
            events.put(eventName, event = new Event<T>());
        }
        event.addListener(listener);
    }
    
    public <T> void removeListener(String eventName, Event.Listener<T> listener) {
        Event<?> genericEvent = events.get(eventName);
        Event<T> event = null;
        if (genericEvent != null) {
            event = (Event<T>)genericEvent;
            event.removeListener(listener);
        }
    }
    
    public <T> void trigger(String eventName, T argument) {
        Event<?> genericEvent = events.get(eventName);
        if (genericEvent == null)
            return;
        ((Event<T>)genericEvent).trigger(argument);
    }
}
