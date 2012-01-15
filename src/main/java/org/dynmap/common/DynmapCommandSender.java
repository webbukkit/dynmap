package org.dynmap.common;

public interface DynmapCommandSender {
    /**
     * Does command sender have given security privilege
     * @param privid - privilege ID
     * @return true if it does, false if it doesn't
     */
    public boolean hasPrivilege(String privid);
    /**
     * Send given message to command sender
     * @param msg - message to be sent (with color codes marked &0 to &F)
     */
    public void sendMessage(String msg);
    /**
     * Test if command sender is still connected/online
     * @return true if connected, false if not
     */
    public boolean isConnected();
    /**
     * Is operator privilege
     */
    public boolean isOp();
}
