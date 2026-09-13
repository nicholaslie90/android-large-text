package vc.east.bigsign;

import java.util.ArrayList;
import java.util.List;

/**
 * The recently-shown signs, newest first. Adding a text that is already in the
 * list moves it to the front rather than duplicating it, so the list stays a
 * set of distinct messages. Pure Java, no Android imports.
 */
public final class History {

    public static final int MAX = 20;

    private final List<SignStyle> entries = new ArrayList<>();

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public SignStyle get(int i) {
        return entries.get(i);
    }

    /** Defensive copy, so callers cannot mutate the stored entries. */
    public List<SignStyle> entries() {
        return new ArrayList<>(entries);
    }

    /**
     * Records a sign. Blank text is ignored. An existing entry with the same
     * text is replaced, so re-showing an old message with new styling updates
     * it in place and promotes it to the front.
     */
    public void add(SignStyle style) {
        if (style == null) return;
        String text = SignStyle.sanitize(style.text);
        if (text.trim().isEmpty()) return;

        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).text.equals(text)) {
                entries.remove(i);
            }
        }
        entries.add(0, new SignStyle(style));
        while (entries.size() > MAX) {
            entries.remove(entries.size() - 1);
        }
    }

    public void remove(int i) {
        if (i >= 0 && i < entries.size()) {
            entries.remove(i);
        }
    }

    public void clear() {
        entries.clear();
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(SignStyle.RECORD);
            sb.append(entries.get(i).serialize());
        }
        return sb.toString();
    }

    /** Unparseable records are skipped rather than failing the whole load. */
    public static History parse(String s) {
        History h = new History();
        if (s == null || s.isEmpty()) return h;
        for (String record : s.split(String.valueOf(SignStyle.RECORD), -1)) {
            if (record.isEmpty()) continue;
            SignStyle style = SignStyle.parse(record);
            if (!style.text.trim().isEmpty() && h.entries.size() < MAX) {
                h.entries.add(style);
            }
        }
        return h;
    }
}
