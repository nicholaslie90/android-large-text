package vc.east.bigsign;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen with two modes. Display mode is a fullscreen sign; tapping it
 * reveals the control panel, and tapping the sign again (or SHOW SIGN) puts it
 * away and records what is on screen in the recents list.
 */
public class MainActivity extends Activity {

    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFF000000, 0xFFFF2D2D, 0xFFFF9500, 0xFFFFE600,
            0xFF34C759, 0xFF00C8FF, 0xFF0A84FF, 0xFFFF2D95, 0xFF8E8E93,
    };

    /** Manual sizes run from 12sp upwards, one seek bar step at a time. */
    private static final float SIZE_BASE_SP = 12f;
    private static final float SIZE_STEP_SP = 3f;

    /** Ticker speeds, in dp per second. */
    private static final int SPEED_MIN_DP = 40;
    private static final int SPEED_MAX_DP = 600;

    private static final int CHIP_MAX_CHARS = 24;

    private SignView sign;
    private ScrollView panel;
    private EditText input;
    private SeekBar sizeBar;
    private SeekBar speedBar;
    private TextView sizeLabel;
    private TextView speedLabel;
    private TextView recentLabel;
    private View recentScroll;
    private LinearLayout recentRow;
    private LinearLayout fgRow;
    private LinearLayout bgRow;
    private Button boldButton;
    private Button fontButton;
    private Button alignButton;
    private Switch marqueeSwitch;

    private final List<View> fgSwatches = new ArrayList<>();
    private final List<View> bgSwatches = new ArrayList<>();

    private Prefs prefs;
    private History history;
    private SignStyle style;

    private boolean editing;
    /** Set while controls are being populated, so listeners do not write back. */
    private boolean syncing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs = new Prefs(this);
        style = prefs.loadStyle();
        history = prefs.loadHistory();

        bindViews();
        buildSwatches(fgRow, true, fgSwatches);
        buildSwatches(bgRow, false, bgSwatches);
        wireControls();

        syncControlsFromStyle();
        rebuildRecents();
        sign.setStyle(style);
        setEditing(style.text.trim().isEmpty());
    }

    private void bindViews() {
        sign = findViewById(R.id.sign);
        panel = findViewById(R.id.panel);
        input = findViewById(R.id.input);
        sizeBar = findViewById(R.id.size);
        speedBar = findViewById(R.id.speed);
        sizeLabel = findViewById(R.id.size_label);
        speedLabel = findViewById(R.id.speed_label);
        recentLabel = findViewById(R.id.recent_label);
        recentScroll = findViewById(R.id.recent_scroll);
        recentRow = findViewById(R.id.recent);
        fgRow = findViewById(R.id.fg_row);
        bgRow = findViewById(R.id.bg_row);
        boldButton = findViewById(R.id.bold);
        fontButton = findViewById(R.id.font);
        alignButton = findViewById(R.id.align);
        marqueeSwitch = findViewById(R.id.marquee);
    }

    private void wireControls() {
        sign.setOnClickListener(v -> setEditing(!editing));
        findViewById(R.id.show).setOnClickListener(v -> setEditing(false));

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing) return;
                style.setText(s.toString());
                sign.setStyle(style);
            }
        });

        sizeBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            void onValue(int progress) {
                style.sizeSp = progress == 0
                        ? SignStyle.AUTO
                        : SIZE_BASE_SP + progress * SIZE_STEP_SP;
                updateSizeLabel();
                sign.setStyle(style);
            }
        });

        speedBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            void onValue(int progress) {
                style.speedDp = SPEED_MIN_DP
                        + Math.round(progress * (SPEED_MAX_DP - SPEED_MIN_DP) / 100f);
                updateSpeedLabel();
                sign.setStyle(style);
            }
        });

        boldButton.setOnClickListener(v -> {
            style.bold = !style.bold;
            updateToggleLabels();
            sign.setStyle(style);
        });

        fontButton.setOnClickListener(v -> {
            style.font = (style.font + 1) % 3;
            updateToggleLabels();
            sign.setStyle(style);
        });

        alignButton.setOnClickListener(v -> {
            style.align = style.align == SignStyle.ALIGN_CENTER
                    ? SignStyle.ALIGN_LEFT
                    : SignStyle.ALIGN_CENTER;
            updateToggleLabels();
            sign.setStyle(style);
        });

        findViewById(R.id.invert).setOnClickListener(v -> {
            int fg = style.fgColor;
            style.fgColor = style.bgColor;
            style.bgColor = fg;
            updateSwatchSelection();
            sign.setStyle(style);
        });

        marqueeSwitch.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
            if (syncing) return;
            style.marquee = checked;
            updateSpeedVisibility();
            sign.setStyle(style);
        });
    }

    // --- Mode -------------------------------------------------------------

    private void setEditing(boolean edit) {
        editing = edit;
        panel.setVisibility(edit ? View.VISIBLE : View.GONE);
        setSystemBarsVisible(edit);
        setMaxBrightness(!edit);

        if (edit) {
            syncing = true;
            input.setText(style.text);
            input.setSelection(input.getText().length());
            syncing = false;
        } else {
            style.setText(input.getText().toString());
            hideKeyboard();
            history.add(style);
            prefs.saveStyle(style);
            prefs.saveHistory(history);
            rebuildRecents();
        }
        sign.setStyle(style);
    }

    /**
     * A sign is meant to be read across a room, so display mode pins the screen
     * to full brightness. Edit mode hands control back to the system.
     */
    private void setMaxBrightness(boolean max) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = max
                ? 1f
                : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    private void setSystemBarsVisible(boolean visible) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(visible);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (visible) {
                    controller.show(WindowInsets.Type.systemBars());
                } else {
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.systemBars());
                }
            }
        } else {
            int flags = visible
                    ? View.SYSTEM_UI_FLAG_VISIBLE
                    : View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !editing) {
            setSystemBarsVisible(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (editing) {
            setEditing(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (editing) {
            style.setText(input.getText().toString());
        }
        prefs.saveStyle(style);
        prefs.saveHistory(history);
    }

    // --- Controls ---------------------------------------------------------

    private void syncControlsFromStyle() {
        syncing = true;
        input.setText(style.text);
        sizeBar.setProgress(sizeToProgress(style.sizeSp));
        speedBar.setProgress(speedToProgress(style.speedDp));
        marqueeSwitch.setChecked(style.marquee);
        syncing = false;

        updateSizeLabel();
        updateSpeedLabel();
        updateSpeedVisibility();
        updateToggleLabels();
        updateSwatchSelection();
    }

    private int sizeToProgress(float sizeSp) {
        if (sizeSp == SignStyle.AUTO) return 0;
        int progress = Math.round((sizeSp - SIZE_BASE_SP) / SIZE_STEP_SP);
        return Math.max(1, Math.min(100, progress));
    }

    private int speedToProgress(int speedDp) {
        int progress = Math.round((speedDp - SPEED_MIN_DP) * 100f / (SPEED_MAX_DP - SPEED_MIN_DP));
        return Math.max(0, Math.min(100, progress));
    }

    private void updateSizeLabel() {
        sizeLabel.setText(style.sizeSp == SignStyle.AUTO
                ? "SIZE — AUTO (fills the screen)"
                : "SIZE — " + Math.round(style.sizeSp) + "sp");
    }

    private void updateSpeedLabel() {
        speedLabel.setText("SPEED — " + style.speedDp + " dp/s");
    }

    private void updateSpeedVisibility() {
        int visibility = style.marquee ? View.VISIBLE : View.GONE;
        speedLabel.setVisibility(visibility);
        speedBar.setVisibility(visibility);
    }

    private void updateToggleLabels() {
        boldButton.setText(style.bold ? "BOLD ON" : "BOLD OFF");
        switch (style.font) {
            case SignStyle.FONT_SERIF:
                fontButton.setText("SERIF");
                break;
            case SignStyle.FONT_MONO:
                fontButton.setText("MONO");
                break;
            default:
                fontButton.setText("SANS");
                break;
        }
        alignButton.setText(style.align == SignStyle.ALIGN_CENTER ? "CENTER" : "LEFT");
    }

    // --- Swatches ---------------------------------------------------------

    private void buildSwatches(LinearLayout row, boolean foreground, List<View> out) {
        int size = dp(40);
        int margin = dp(5);
        for (int color : PALETTE) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(margin);
            swatch.setLayoutParams(lp);
            swatch.setTag(color);
            swatch.setOnClickListener(v -> {
                if (foreground) {
                    style.fgColor = color;
                } else {
                    style.bgColor = color;
                }
                updateSwatchSelection();
                sign.setStyle(style);
            });
            row.addView(swatch);
            out.add(swatch);
        }
    }

    private void updateSwatchSelection() {
        paintSwatches(fgSwatches, style.fgColor);
        paintSwatches(bgSwatches, style.bgColor);
    }

    private void paintSwatches(List<View> swatches, int selectedColor) {
        for (View swatch : swatches) {
            int color = (Integer) swatch.getTag();
            boolean selected = color == selectedColor;
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            shape.setStroke(selected ? dp(3) : dp(1), selected ? 0xFFFFFFFF : 0xFF555555);
            swatch.setBackground(shape);
        }
    }

    // --- Recents ----------------------------------------------------------

    private void rebuildRecents() {
        recentRow.removeAllViews();
        int visibility = history.isEmpty() ? View.GONE : View.VISIBLE;
        recentLabel.setVisibility(visibility);
        recentScroll.setVisibility(visibility);

        for (int i = 0; i < history.size(); i++) {
            final int index = i;
            final SignStyle entry = history.get(i);
            recentRow.addView(createChip(entry, index));
        }
    }

    /** Each chip previews the colours the message was last shown in. */
    private View createChip(SignStyle entry, int index) {
        TextView chip = new TextView(this);
        String label = entry.text.replace('\n', ' ');
        if (label.length() > CHIP_MAX_CHARS) {
            label = label.substring(0, CHIP_MAX_CHARS - 1) + "…";
        }
        chip.setText(label);
        chip.setSingleLine(true);
        chip.setTextColor(entry.fgColor);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setPadding(dp(14), dp(10), dp(14), dp(10));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(18));
        shape.setColor(entry.bgColor);
        shape.setStroke(dp(1), 0xFF555555);
        chip.setBackground(shape);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            style = new SignStyle(entry);
            syncControlsFromStyle();
            sign.setStyle(style);
        });
        chip.setOnLongClickListener(v -> {
            history.remove(index);
            prefs.saveHistory(history);
            rebuildRecents();
            Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
            return true;
        });
        return chip;
    }

    // --- Small helpers ----------------------------------------------------

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    /** Spares every seek bar the two callbacks it does not care about. */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {

        abstract void onValue(int progress);

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                onValue(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
