package org.dynmap;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listener class for the Dynmap integrated web server lifecycle, will only work if the internal webserver is actually being used.
 */
public abstract class DynmapWebserverStateListener
{
    /**
     * Status flag indicating if the web server is online or offline.
     */
    private static boolean webserverStarted = false;

    private static final CopyOnWriteArrayList<DynmapWebserverStateListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Called when API becomes enabled, or during call to register listener if API is already enabled
     */
    public abstract void webserverStarted();

    /**
     * Called when API becomes enabled, or during call to register listener if API is already enabled
     */
    public abstract void webserverStopped();

    /**
     * This method is fired to indicate the listener that the internal web server is not enabled, thus indicating that {@link DynmapWebserverStateListener#webserverStarted()} and {@link DynmapWebserverStateListener#webserverStopped()} will not fire.
     */
    public void webserverDisabled() {}

    /**
     * Register listener instance
     *
     * @param listener - listener to register
     */
    public static void register(DynmapWebserverStateListener listener)
    {
        listeners.add(listener);
        if (webserverStarted)
        {
            listener.webserverStarted();
        }
    }

    /**
     * Unregister listener instance
     *
     * @param listener - listener to unregister
     */
    public static void unregister(DynmapWebserverStateListener listener) {
        listeners.remove(listener);
    }

    // Internal call - MODS/PLUGINS MUST NOT USE
    public static void stateWebserverStarted()
    {
        if (webserverStarted)
        {
            stateWebserverStopped();
        }
        webserverStarted = true;
        for (DynmapWebserverStateListener l : listeners)
        {
            l.webserverStarted();
        }
    }

    // Internal call - MODS/PLUGINS MUST NOT USE
    public static void stateWebserverStopped()
    {
        for (DynmapWebserverStateListener l : listeners)
        {
            l.webserverStopped();
        }
        webserverStarted = false;
    }

    // Internal call - MODS/PLUGINS MUST NOT USE
    public static void stateWebserverDisabled()
    {
        for (DynmapWebserverStateListener l : listeners)
        {
            l.webserverDisabled();
        }
    }
}
