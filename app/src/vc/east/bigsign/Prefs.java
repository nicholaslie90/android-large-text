package vc.east.bigsign;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the current sign and the recents list in SharedPreferences. */
final class Prefs {

    private static final String FILE = "bigsign";
    private static final String KEY_STYLE = "style";
    private static final String KEY_HISTORY = "history";

    private final SharedPreferences prefs;

    Prefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * The sign to open on: whatever was last shown, falling back to the newest
     * entry in the recents list if that is missing or blank, so the app never
     * opens on an empty sign while there is a recent message to reuse.
     */
    SignStyle loadStyle() {
        String stored = prefs.getString(KEY_STYLE, null);
        if (stored != null) {
            SignStyle style = SignStyle.parse(stored);
            if (!style.text.trim().isEmpty()) return style;
        }
        History history = loadHistory();
        if (!history.isEmpty()) return new SignStyle(history.get(0));

        SignStyle first = new SignStyle();
        first.setText("HELLO");
        return first;
    }

    void saveStyle(SignStyle style) {
        prefs.edit().putString(KEY_STYLE, style.serialize()).apply();
    }

    History loadHistory() {
        return History.parse(prefs.getString(KEY_HISTORY, null));
    }

    void saveHistory(History history) {
        prefs.edit().putString(KEY_HISTORY, history.serialize()).apply();
    }
}
