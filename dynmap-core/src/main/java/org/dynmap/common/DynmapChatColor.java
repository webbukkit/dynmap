package org.dynmap.common;


public enum DynmapChatColor {
    BLACK(0x0),
    DARK_BLUE(0x1),
    DARK_GREEN(0x2),
    DARK_AQUA(0x3),
    DARK_RED(0x4),
    DARK_PURPLE(0x5),
    GOLD(0x6),
    GRAY(0x7),
    DARK_GRAY(0x8),
    BLUE(0x9),
    GREEN(0xA),
    AQUA(0xB),
    RED(0xC),
    LIGHT_PURPLE(0xD),
    YELLOW(0xE),
    WHITE(0xF);

    private final String str;

    private DynmapChatColor(final int code) {
        this.str = String.format("\u00A7%x", code);
    }
    @Override
    public String toString() {
        return str;
    }
    public static String stripColor(final String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("(?i)\u00A7[0-9A-Za-z]", "");
    }
}
