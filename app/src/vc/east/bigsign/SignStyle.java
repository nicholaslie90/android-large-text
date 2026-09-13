package vc.east.bigsign;

/**
 * Everything that defines how one sign looks. Pure Java, no Android imports,
 * so it can be unit-tested on the host JVM.
 */
public final class SignStyle {

    public static final int FONT_SANS = 0;
    public static final int FONT_SERIF = 1;
    public static final int FONT_MONO = 2;

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;

    /** ASCII unit separator: splits fields within one style. */
    static final char FIELD = (char) 0x1F;
    /** ASCII record separator: splits styles within the history list. */
    static final char RECORD = (char) 0x1E;

    /** Leading field of every record, so old formats can still be read. */
    private static final int VERSION = 2;
    private static final int FIELD_COUNT = 9;
    /** Version 1 carried a manual text size; the sign now always auto-fits. */
    private static final int FIELD_COUNT_V1 = 10;

    public String text = "";
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
                .append(VERSION).append(FIELD)
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

    /**
     * Returns a default style if the input is missing or unparseable. Version 1
     * records are still read: their manual size field is skipped, so upgrading
     * keeps the recents list rather than silently emptying it.
     */
    public static SignStyle parse(String s) {
        SignStyle out = new SignStyle();
        if (s == null || s.isEmpty()) return out;

        boolean v1 = s.startsWith("1" + FIELD);
        int count = v1 ? FIELD_COUNT_V1 : FIELD_COUNT;
        String[] p = s.split(String.valueOf(FIELD), count);
        if (p.length < count) return out;

        int i = v1 ? 2 : 1;
        try {
            out.fgColor = Integer.parseInt(p[i++]);
            out.bgColor = Integer.parseInt(p[i++]);
            out.bold = "1".equals(p[i++]);
            out.font = Integer.parseInt(p[i++]);
            out.align = Integer.parseInt(p[i++]);
            out.marquee = "1".equals(p[i++]);
            out.speedDp = Integer.parseInt(p[i++]);
            out.text = p[i];
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
