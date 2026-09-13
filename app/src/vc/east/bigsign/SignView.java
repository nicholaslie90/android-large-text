package vc.east.bigsign;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

import java.util.List;

/**
 * Draws one sign: the text as large as it will go, or scrolling across the
 * screen as a ticker. All the wrapping and sizing arithmetic lives in
 * {@link TextFitter}; this class only measures glyphs and paints them.
 */
public class SignView extends View {

    /** Auto-fit search bounds, in sp. The top end is well past any phone screen. */
    private static final float MIN_SP = 8f;
    private static final float MAX_SP = 900f;

    /** Blank space between the end of the ticker text and its repeat. */
    private static final float MARQUEE_GAP_FRACTION = 0.3f;
    private static final float MARQUEE_MIN_GAP_DP = 48f;

    /** Caps the step after the app was backgrounded, so the ticker cannot jump. */
    private static final float MAX_FRAME_SECONDS = 0.05f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint measurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextFitter.Measurer measurer = new PaintMeasurer();

    private SignStyle style = new SignStyle();
    private TextFitter.Fit fit;
    private float density = 1f;

    private float marqueeOffset;
    private long lastFrameMs;

    public SignView(Context context) {
        super(context);
        init();
    }

    public SignView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SignView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        applyTypeface();
    }

    public void setStyle(SignStyle newStyle) {
        boolean restartTicker = style.marquee != newStyle.marquee
                || !style.text.equals(newStyle.text);
        style = new SignStyle(newStyle);
        applyTypeface();
        fit = null;
        if (restartTicker) {
            marqueeOffset = 0f;
            lastFrameMs = 0L;
        }
        invalidate();
    }

    public SignStyle getStyle() {
        return new SignStyle(style);
    }

    private void applyTypeface() {
        Typeface base;
        switch (style.font) {
            case SignStyle.FONT_SERIF:
                base = Typeface.SERIF;
                break;
            case SignStyle.FONT_MONO:
                base = Typeface.MONOSPACE;
                break;
            default:
                base = Typeface.SANS_SERIF;
                break;
        }
        Typeface typeface = Typeface.create(base, style.bold ? Typeface.BOLD : Typeface.NORMAL);
        paint.setTypeface(typeface);
        measurePaint.setTypeface(typeface);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        fit = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lastFrameMs = 0L;
    }

    private float contentWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private float contentHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    /**
     * Re-fits the text to whatever box the view currently has. The size is never
     * chosen by hand, so this runs again on every rotation and every time the
     * control panel opens or closes.
     */
    private void ensureFit() {
        if (fit != null) {
            return;
        }
        float min = MIN_SP * density;
        float max = MAX_SP * density;

        // A ticker is one unwrapped line, so it is only ever bounded by height.
        String text = style.marquee ? style.text.replace('\n', ' ') : style.text;
        float width = style.marquee ? Float.MAX_VALUE / 4f : contentWidth();

        fit = TextFitter.fit(text, width, contentHeight(), min, max, measurer);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(style.bgColor);
        ensureFit();

        paint.setColor(style.fgColor);
        paint.setTextSize(fit.size);

        // Centring uses the same ink block the fitter sized against, so the
        // text is centred on its glyphs rather than on the font's line boxes.
        TextFitter.Block block = TextFitter.measure(fit.lines, fit.size, measurer);
        float top = getPaddingTop() + (contentHeight() - block.height) / 2f;

        if (style.marquee) {
            drawMarquee(canvas, top + block.firstBaseline);
        } else {
            drawStatic(canvas, top + block.firstBaseline);
        }
    }

    private void drawStatic(Canvas canvas, float firstBaseline) {
        List<String> lines = fit.lines;
        float lineHeight = measurer.lineHeight(fit.size);

        boolean centered = style.align == SignStyle.ALIGN_CENTER;
        paint.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
        float x = centered
                ? getPaddingLeft() + contentWidth() / 2f
                : getPaddingLeft();

        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), x, firstBaseline + i * lineHeight, paint);
        }
    }

    private void drawMarquee(Canvas canvas, float baseline) {
        String text = fit.lines.isEmpty() ? "" : fit.lines.get(0);
        paint.setTextAlign(Paint.Align.LEFT);

        float textWidth = paint.measureText(text);
        float gap = Math.max(contentWidth() * MARQUEE_GAP_FRACTION, MARQUEE_MIN_GAP_DP * density);
        float period = textWidth + gap;
        if (period <= 0f) {
            return;
        }

        long now = AnimationUtils.currentAnimationTimeMillis();
        if (lastFrameMs != 0L) {
            float seconds = Math.min((now - lastFrameMs) / 1000f, MAX_FRAME_SECONDS);
            marqueeOffset = (marqueeOffset + style.speedDp * density * seconds) % period;
        }
        lastFrameMs = now;

        float x = getPaddingLeft() - marqueeOffset;
        canvas.drawText(text, x, baseline, paint);
        // The repeat keeps the line continuous as the first copy leaves the screen.
        canvas.drawText(text, x + period, baseline, paint);

        postInvalidateOnAnimation();
    }

    /** Backs {@link TextFitter} with real glyph metrics from a scratch Paint. */
    private final class PaintMeasurer implements TextFitter.Measurer {

        private final Rect bounds = new Rect();

        @Override
        public float width(String s, float size) {
            measurePaint.setTextSize(size);
            return measurePaint.measureText(s);
        }

        @Override
        public float lineHeight(float size) {
            measurePaint.setTextSize(size);
            Paint.FontMetrics fm = measurePaint.getFontMetrics();
            return fm.bottom - fm.top;
        }

        @Override
        public float inkAscent(String s, float size) {
            // getTextBounds reports the drawn glyphs, so a line of caps measures
            // its cap height and not the ascent the font reserves above it.
            return -inkBounds(s, size).top;
        }

        @Override
        public float inkDescent(String s, float size) {
            return inkBounds(s, size).bottom;
        }

        private Rect inkBounds(String s, float size) {
            measurePaint.setTextSize(size);
            measurePaint.getTextBounds(s, 0, s.length(), bounds);
            if (s.trim().isEmpty()) {
                // Whitespace has no ink; keep it from collapsing to a stray box.
                bounds.top = 0;
                bounds.bottom = 0;
            }
            return bounds;
        }
    }
}
