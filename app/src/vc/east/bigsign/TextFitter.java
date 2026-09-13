package vc.east.bigsign;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps text and finds the largest size that still fits a box. Text measurement
 * is injected, so this holds no Android dependency and is testable on the host
 * JVM with a stub measurer.
 */
public final class TextFitter {

    /** How the host measures text; on Android this is backed by {@code Paint}. */
    public interface Measurer {
        /** Width of {@code s} rendered at {@code size}. */
        float width(String s, float size);

        /** Baseline-to-baseline distance at {@code size}. */
        float lineHeight(float size);
    }

    /** A chosen size together with the lines it wraps into. */
    public static final class Fit {
        public final float size;
        public final List<String> lines;

        Fit(float size, List<String> lines) {
            this.size = size;
            this.lines = lines;
        }
    }

    /** Stop bisecting once the remaining range is smaller than this, in px. */
    private static final float PRECISION = 0.5f;

    private TextFitter() {
    }

    /**
     * Largest size in [minSize, maxSize] whose wrapped text fits maxW by maxH.
     * Falls back to minSize when nothing fits, so there is always something to
     * draw.
     *
     * <p>Words are kept whole. Splitting one across lines would often allow a
     * larger size — "HELLO" as "HEL" over "LO" fills more of a narrow screen —
     * but a sign that does that is unreadable, so the size gives way instead.
     * A word too long to fit on its own line even at {@code minSize} is the one
     * case that still gets broken.
     */
    public static Fit fit(String text, float maxW, float maxH,
                          float minSize, float maxSize, Measurer m) {
        if (text == null || text.isEmpty()) {
            List<String> empty = new ArrayList<>();
            empty.add("");
            return new Fit(maxSize, empty);
        }
        if (maxW <= 0 || maxH <= 0) {
            return new Fit(minSize, wrap(text, Float.MAX_VALUE, minSize, m));
        }

        boolean mustBreakWords = !fits(text, maxW, maxH, minSize, m, false);
        return search(text, maxW, maxH, minSize, maxSize, m, mustBreakWords);
    }

    private static Fit search(String text, float maxW, float maxH,
                              float minSize, float maxSize, Measurer m, boolean breakWords) {
        if (fits(text, maxW, maxH, maxSize, m, breakWords)) {
            return new Fit(maxSize, wrap(text, maxW, maxSize, m, breakWords));
        }

        float lo = minSize;
        float hi = maxSize;
        while (hi - lo > PRECISION) {
            float mid = (lo + hi) / 2f;
            if (fits(text, maxW, maxH, mid, m, breakWords)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return new Fit(lo, wrap(text, maxW, lo, m, breakWords));
    }

    private static boolean fits(String text, float maxW, float maxH, float size,
                                Measurer m, boolean breakWords) {
        List<String> lines = wrap(text, maxW, size, m, breakWords);
        if (lines.size() * m.lineHeight(size) > maxH) {
            return false;
        }
        for (String line : lines) {
            // Too wide means either an unbreakable glyph, or a whole word that
            // we have chosen not to break.
            if (m.width(line, size) > maxW) {
                return false;
            }
        }
        return true;
    }

    /**
     * Greedy word wrap. Explicit newlines are kept as line breaks; a word wider
     * than the box on its own is broken between characters.
     */
    public static List<String> wrap(String text, float maxW, float size, Measurer m) {
        return wrap(text, maxW, size, m, true);
    }

    /**
     * As {@link #wrap(String, float, float, Measurer)}, but when
     * {@code breakWords} is false an over-wide word is left to overflow the line
     * rather than being split — which the caller reads as "does not fit".
     */
    public static List<String> wrap(String text, float maxW, float size, Measurer m,
                                    boolean breakWords) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, maxW, size, m, breakWords, out);
        }
        return out;
    }

    private static void wrapParagraph(String paragraph, float maxW, float size,
                                      Measurer m, boolean breakWords, List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ", -1)) {
            if (line.length() == 0) {
                appendWord(word, maxW, size, m, breakWords, out, line);
                continue;
            }
            String candidate = line + " " + word;
            if (m.width(candidate, size) <= maxW) {
                line.setLength(0);
                line.append(candidate);
            } else {
                out.add(line.toString());
                line.setLength(0);
                appendWord(word, maxW, size, m, breakWords, out, line);
            }
        }
        out.add(line.toString());
    }

    /**
     * Starts a fresh line with {@code word}, breaking it across lines when it is
     * too wide to fit on one. Leaves the trailing remainder in {@code line}.
     */
    private static void appendWord(String word, float maxW, float size,
                                   Measurer m, boolean breakWords,
                                   List<String> out, StringBuilder line) {
        if (!breakWords || m.width(word, size) <= maxW) {
            line.append(word);
            return;
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (line.length() > 0 && m.width(line.toString() + c, size) > maxW) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(c);
        }
    }
}
