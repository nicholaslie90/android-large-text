package vc.east.bigsign;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
     * A manual size is passed as both bounds of the search, so the fitter returns
     * exactly that size and only does the wrapping.
     */
    private void ensureFit() {
        if (fit != null) {
            return;
        }
        boolean auto = style.sizeSp == SignStyle.AUTO;
        float min = auto ? MIN_SP * density : style.sizeSp * density;
        float max = auto ? MAX_SP * density : style.sizeSp * density;

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
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = fm.bottom - fm.top;

        if (style.marquee) {
            drawMarquee(canvas, fm, lineHeight);
        } else {
            drawStatic(canvas, fm, lineHeight);
        }
    }

    private void drawStatic(Canvas canvas, Paint.FontMetrics fm, float lineHeight) {
        List<String> lines = fit.lines;
        float blockHeight = lines.size() * lineHeight;
        float top = getPaddingTop() + (contentHeight() - blockHeight) / 2f;

        boolean centered = style.align == SignStyle.ALIGN_CENTER;
        paint.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
        float x = centered
                ? getPaddingLeft() + contentWidth() / 2f
                : getPaddingLeft();

        for (int i = 0; i < lines.size(); i++) {
            float baseline = top + i * lineHeight - fm.top;
            canvas.drawText(lines.get(i), x, baseline, paint);
        }
    }

    private void drawMarquee(Canvas canvas, Paint.FontMetrics fm, float lineHeight) {
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

        float baseline = getPaddingTop() + (contentHeight() - lineHeight) / 2f - fm.top;
        float x = getPaddingLeft() - marqueeOffset;
        canvas.drawText(text, x, baseline, paint);
        // The repeat keeps the line continuous as the first copy leaves the screen.
        canvas.drawText(text, x + period, baseline, paint);

        postInvalidateOnAnimation();
    }

    /** Backs {@link TextFitter} with real glyph metrics from a scratch Paint. */
    private final class PaintMeasurer implements TextFitter.Measurer {

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
    }
}
