package vc.east.bigsign;

/**
 * A predictable stand-in for Paint: every glyph is half an em wide, and the ink
 * sits well inside the line box, the way a real font's caps do — 0.7em above the
 * baseline and 0.1em below, out of the 1.2em the line reserves.
 */
final class StubMeasurer implements TextFitter.Measurer {

    @Override
    public float width(String s, float size) {
        return s.length() * size * 0.5f;
    }

    @Override
    public float lineHeight(float size) {
        return size * 1.2f;
    }

    @Override
    public float inkAscent(String s, float size) {
        return s.isEmpty() ? 0f : size * 0.7f;
    }

    @Override
    public float inkDescent(String s, float size) {
        return s.isEmpty() ? 0f : size * 0.1f;
    }
}
