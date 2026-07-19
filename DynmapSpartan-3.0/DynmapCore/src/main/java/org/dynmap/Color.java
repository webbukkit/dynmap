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
        int alpha = val & 0xFF000000;
        int num = (((val >> 16) & 0xFF) * 76)
                + (((val >> 8)  & 0xFF) * 151)
                + (( val        & 0xFF) * 28);
        // weights sum to 255, so num ∈ [0, 65025]; fast /255 via shift
        int gray = (num + (num >> 8) + 1) >> 8;
        val = alpha | (gray << 16) | (gray << 8) | gray;
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
        val = (mulDiv255(val >>> 24,         argb >>> 24        ) << 24)
            | (mulDiv255((val >> 16) & 0xFF, (argb >> 16) & 0xFF) << 16)
            | (mulDiv255((val >> 8)  & 0xFF, (argb >> 8)  & 0xFF) << 8)
            |  mulDiv255( val        & 0xFF,  argb        & 0xFF);
    }
    /**
     * Scale each color component, based on the corresponding component
     * @param argb0 - first color
     * @param argb1 second color
     * @return blended color
     */
    public static final int blendColor(int argb0, int argb1) {
        return (mulDiv255(argb0 >>> 24,          argb1 >>> 24        ) << 24)
             | (mulDiv255((argb0 >> 16) & 0xFF,  (argb1 >> 16) & 0xFF) << 16)
             | (mulDiv255((argb0 >> 8)  & 0xFF,  (argb1 >> 8)  & 0xFF) << 8)
             |  mulDiv255( argb0        & 0xFF,   argb1        & 0xFF);
    }
    /**
     * Scale the RGB channels by scale/256, leaving alpha unchanged.
     * Equivalent to setRGBA(getRed()*scale>>8, getGreen()*scale>>8, getBlue()*scale>>8, getAlpha())
     * but avoids redundant unpack/repack of the alpha channel.
     * @param scale - scale factor 0..256 (256 = full brightness)
     */
    public final void scaleRGB(int scale) {
        val = (val & 0xFF000000)
            | ((((val >> 16) & 0xFF) * scale >> 8) << 16)
            | ((((val >> 8)  & 0xFF) * scale >> 8) << 8)
            |  ((val         & 0xFF) * scale >> 8);
    }
    /**
     * Fast multiply-then-divide-by-255 for two values a, b each in [0, 255].
     * Returns floor(a*b/255), equivalent to the standard integer division but
     * computed with shifts only: (x + (x >> 8) + 1) >> 8 where x = a * b.
     */
    private static int mulDiv255(int a, int b) {
        int x = a * b;
        return (x + (x >> 8) + 1) >> 8;
    }
}
