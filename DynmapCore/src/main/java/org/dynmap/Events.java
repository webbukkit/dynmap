package org.dynmap;
import java.util.HashMap;
import java.util.Map;


public class Events {
    public Map<String, Event<?>> events = new HashMap<String, Event<?>>();
    @SuppressWarnings("unchecked")
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
    
    @SuppressWarnings("unchecked")
    public <T> void removeListener(String eventName, Event.Listener<T> listener) {
        Event<?> genericEvent = events.get(eventName);
        Event<T> event = null;
        if (genericEvent != null) {
            event = (Event<T>)genericEvent;
            event.removeListener(listener);
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> void trigger(String eventName, T argument) {
        Event<?> genericEvent = events.get(eventName);
        if (genericEvent == null)
            return;
        ((Event<T>)genericEvent).trigger(argument);
    }
    @SuppressWarnings("unchecked")
    public <T> void triggerSync(DynmapCore core, String eventName, T argument) {
        Event<?> genericEvent = events.get(eventName);
        if (genericEvent == null)
            return;
        ((Event<T>)genericEvent).triggerSync(core, argument);
    }
}
