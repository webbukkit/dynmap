package org.dynmap;

/**
 * Simple replacement for java.awt.Color for dynmap - it's not an invariant, so we don't make millions
 * of them during rendering
 */
public class Color {
    /* ARGB value */
    private int val;

    public static final int TRANSPARENT = 0;

    public Color(int red, int green, int blue, int alpha) {
        setRGBA(red, green, blue, alpha);
    }
    public Color(int red, int green, int blue) {
        setRGBA(red, green, blue, 0xFF);
    }
    public Color() {
        setTransparent();
    }
    public final int getRed() {
        return (val >> 16) & 0xFF;
    }
    public final int getGreen() {
        return (val >> 8) & 0xFF;
    }
    public final int getBlue() {
        return val & 0xFF;
    }
    public final int getAlpha() {
        return ((val >> 24) & 0xFF);
    }
    public final boolean isTransparent() {
        return ((val & 0xFF000000) == TRANSPARENT);
    }
    public final void setTransparent() {
        val = TRANSPARENT;
    }
    public final void setGrayscale() {
        int alpha = (val >> 24) & 0xFF;
        int red = (val >> 16) & 0xFF;
        int green = (val >> 8) & 0xFF;
        int blue = val & 0xFF;
        int gray = ((red * 76) + (green * 151) + (blue * 28)) / 255;
        setRGBA(gray, gray, gray, alpha);
    }
    public final void setColor(Color c) {
        val = c.val;
    }
    public final void setRGBA(int red, int green, int blue, int alpha) {
        val = ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }
    public final int getARGB() {
        return val;
    }
    public final void setARGB(int c) {
        val = c;
    }
    public final int getComponent(int idx) {
        return 0xFF & (val >> ((3-idx)*8));
    }
    public final void setAlpha(int v) {
        val = (val & 0x00FFFFFF) | (v << 24);
    }
    public final void scaleColor(Color minimum, Color maximum) {
        int alpha = (val >> 24) & 0xFF;
        int red = (val >> 16) & 0xFF;
        int green = (val >> 8) & 0xFF;
        int blue = val & 0xFF;
        red = minimum.getRed() + ((maximum.getRed() - minimum.getRed()) * red) / 256;
        green = minimum.getGreen() + ((maximum.getGreen() - minimum.getGreen()) * green) / 256;
        blue = minimum.getBlue() + ((maximum.getBlue() - minimum.getBlue()) * blue) / 256;
        setRGBA(red, green, blue, alpha);
    }
    /**
     * Scale each color component, based on the corresponding component
     * @param c - color to blend
     */
    public final void blendColor(Color c) {
        blendColor(c.val);
    }
    /**
     * Scale each color component, based on the corresponding component
     * @param argb - ARGB to blend
     */
    public final void blendColor(int argb) {
        int nval = (((((val >> 24) & 0xFF) * ((argb >> 24) & 0xFF)) / 255) << 24);
        nval = nval | (((((val >> 16) & 0xFF) * ((argb >> 16) & 0xFF)) / 255) << 16);
        nval = nval | (((((val >> 8) & 0xFF) * ((argb >> 8) & 0xFF)) / 255) << 8);
        nval = nval | (((val & 0xFF) * (argb & 0xFF)) / 255);
        val = nval;
    }
    /**
     * Scale each color component, based on the corresponding component
     * @param argb0 - first color
     * @param argb1 second color
     * @return blended color
     */
    public static final int blendColor(int argb0, int argb1) {
        int nval = (((((argb0 >> 24) & 0xFF) * ((argb1 >> 24) & 0xFF)) / 255) << 24);
        nval = nval | (((((argb0 >> 16) & 0xFF) * ((argb1 >> 16) & 0xFF)) / 255) << 16);
        nval = nval | (((((argb0 >> 8) & 0xFF) * ((argb1 >> 8) & 0xFF)) / 255) << 8);
        nval = nval | (((argb0 & 0xFF) * (argb1 & 0xFF)) / 255);
        return nval;
    }
}
