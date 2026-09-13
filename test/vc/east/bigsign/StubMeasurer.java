package vc.east.bigsign;

/** A predictable stand-in for Paint: every glyph is half an em wide. */
final class StubMeasurer implements TextFitter.Measurer {

    @Override
    public float width(String s, float size) {
        return s.length() * size * 0.5f;
    }

    @Override
    public float lineHeight(float size) {
        return size * 1.2f;
    }
}
