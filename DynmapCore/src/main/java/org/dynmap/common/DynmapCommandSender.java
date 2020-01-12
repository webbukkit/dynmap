package org.dynmap.common;

public interface DynmapCommandSender {
    /**
     * Does command sender have given security privilege
     *
     * @param privid - privilege ID
     * @return true if it does, false if it doesn't
     */
    boolean hasPrivilege(String privid);

    /**
     * Send given message to command sender
     *
     * @param msg - message to be sent (with color codes marked &amp;0 to &amp;F)
     */
    void sendMessage(String msg);

    /**
     * Test if command sender is still connected/online
     *
     * @return true if connected, false if not
     */
    boolean isConnected();

    /**
     * Is operator privilege
     *
     * @return true if operator
     */
    boolean isOp();

    /**
     * Test for permission node (no dynmap. prefix assumed)
     *
     * @param node - permission ID
     * @return true if allowed
     */
    boolean hasPermissionNode(String node);
}
