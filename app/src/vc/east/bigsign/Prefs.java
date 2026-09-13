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

    SignStyle loadStyle() {
        String stored = prefs.getString(KEY_STYLE, null);
        if (stored == null) {
            SignStyle first = new SignStyle();
            first.setText("HELLO");
            return first;
        }
        return SignStyle.parse(stored);
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
