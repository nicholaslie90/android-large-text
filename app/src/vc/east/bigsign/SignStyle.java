package vc.east.bigsign;

/**
 * Everything that defines how one sign looks. Pure Java, no Android imports,
 * so it can be unit-tested on the host JVM.
 */
public final class SignStyle {

    /** Sentinel for {@link #sizeSp}: pick the largest size that fits the screen. */
    public static final float AUTO = -1f;

    public static final int FONT_SANS = 0;
    public static final int FONT_SERIF = 1;
    public static final int FONT_MONO = 2;

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;

    /** ASCII unit separator: splits fields within one style. */
    static final char FIELD = (char) 0x1F;
    /** ASCII record separator: splits styles within the history list. */
    static final char RECORD = (char) 0x1E;

    private static final int FIELD_COUNT = 10;

    public String text = "";
    public float sizeSp = AUTO;
    public int fgColor = 0xFFFFFFFF;
    public int bgColor = 0xFF000000;
    public boolean bold = true;
    public int font = FONT_SANS;
    public int align = ALIGN_CENTER;
    public boolean marquee = false;
    /** Marquee scroll speed in density-independent pixels per second. */
    public int speedDp = 200;

    public SignStyle() {
    }

    public SignStyle(SignStyle other) {
        text = other.text;
        sizeSp = other.sizeSp;
        fgColor = other.fgColor;
        bgColor = other.bgColor;
        bold = other.bold;
        font = other.font;
        align = other.align;
        marquee = other.marquee;
        speedDp = other.speedDp;
    }

    /**
     * Strips the two separator characters. Neither is typeable, so this only
     * ever fires on pasted junk, but it keeps the storage format total.
     */
    public static String sanitize(String s) {
        if (s == null) return "";
        return s.replace(FIELD, ' ').replace(RECORD, ' ');
    }

    public void setText(String s) {
        text = sanitize(s);
    }

    /** Text is serialized last so a stray separator cannot shift other fields. */
    public String serialize() {
        return new StringBuilder()
                .append(1).append(FIELD)
                .append(sizeSp).append(FIELD)
                .append(fgColor).append(FIELD)
                .append(bgColor).append(FIELD)
                .append(bold ? 1 : 0).append(FIELD)
                .append(font).append(FIELD)
                .append(align).append(FIELD)
                .append(marquee ? 1 : 0).append(FIELD)
                .append(speedDp).append(FIELD)
                .append(sanitize(text))
                .toString();
    }

    /** Returns a default style if the input is missing or unparseable. */
    public static SignStyle parse(String s) {
        SignStyle out = new SignStyle();
        if (s == null || s.isEmpty()) return out;
        String[] p = s.split(String.valueOf(FIELD), FIELD_COUNT);
        if (p.length < FIELD_COUNT) return out;
        try {
            out.sizeSp = Float.parseFloat(p[1]);
            out.fgColor = Integer.parseInt(p[2]);
            out.bgColor = Integer.parseInt(p[3]);
            out.bold = "1".equals(p[4]);
            out.font = Integer.parseInt(p[5]);
            out.align = Integer.parseInt(p[6]);
            out.marquee = "1".equals(p[7]);
            out.speedDp = Integer.parseInt(p[8]);
            out.text = p[9];
        } catch (NumberFormatException e) {
            return new SignStyle();
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignStyle)) return false;
        return serialize().equals(((SignStyle) o).serialize());
    }

    @Override
    public int hashCode() {
        return serialize().hashCode();
    }
}
