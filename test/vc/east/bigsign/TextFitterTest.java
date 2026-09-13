package vc.east.bigsign;

import java.util.List;

final class TextFitterTest {

    private static final StubMeasurer M = new StubMeasurer();

    static void run() {
        usesMaxSizeWhenItFits();
        shrinksToFitWidth();
        keepsWordsWholeRatherThanGrowing();
        wrapsAtSpacesRatherThanGrowing();
        breaksOnlyWhenAWordCannotFitAtAll();
        shrinksToFitHeight();
        fillsTheHeightWithInkRatherThanFontPadding();
        leadsMultipleLinesByTheFontButBoundsThemByTheirInk();
        measuresABlockFromItsInk();
        keepsBlankLinesHoldingTheirLineOpen();
        wrapsOnSpaces();
        keepsExplicitNewlines();
        breaksWordsTooLongToWrap();
        handlesEmptyText();
        survivesZeroSizedBox();
    }

    private static void usesMaxSizeWhenItFits() {
        TextFitter.Fit fit = TextFitter.fit("HI", 1000, 1000, 8, 400, M);
        Assert.near("uncapped size", 400, fit.size, 0.001f);
        Assert.lines("single line", fit.lines, "HI");
    }

    private static void shrinksToFitWidth() {
        // Two glyphs at half an em each means width == size, so a 100px-wide box
        // caps the size at 100. The height only has room for the one line.
        TextFitter.Fit fit = TextFitter.fit("HI", 100, 130, 8, 400, M);
        Assert.near("width-bound size", 100, fit.size, 1f);
        Assert.lines("still one line", fit.lines, "HI");
    }

    private static void keepsWordsWholeRatherThanGrowing() {
        // Splitting "HI" into "H" over "I" would allow twice the size in this
        // tall box. A sign that does that is unreadable, so the size gives way.
        TextFitter.Fit fit = TextFitter.fit("HI", 100, 1000, 8, 400, M);
        Assert.near("word-preserving size", 100, fit.size, 1f);
        Assert.lines("word kept whole", fit.lines, "HI");
    }

    private static void wrapsAtSpacesRatherThanGrowing() {
        // Likewise across words: "AB CD" stays two clean lines, not "ABC"/"D".
        TextFitter.Fit fit = TextFitter.fit("AB CD", 100, 1000, 8, 400, M);
        Assert.lines("split at the space", fit.lines, "AB", "CD");
    }

    private static void breaksOnlyWhenAWordCannotFitAtAll() {
        // Twelve glyphs in a 20px box: no size keeps this word whole, so the
        // last-resort character break is allowed.
        TextFitter.Fit fit = TextFitter.fit("WWWWWWWWWWWW", 20, 1000, 8, 400, M);
        Assert.equal("broken into single glyphs", 12, fit.lines.size());
        Assert.near("as large as the box allows", 40, fit.size, 1f);
    }

    private static void shrinksToFitHeight() {
        // One line of ink 0.8 * size tall must fit 60px.
        TextFitter.Fit fit = TextFitter.fit("HI", 10000, 60, 8, 400, M);
        Assert.near("height-bound size", 75, fit.size, 1f);
    }

    private static void fillsTheHeightWithInkRatherThanFontPadding() {
        // The whole point of the sign: a height-bound line should end up with
        // its glyphs touching top and bottom. Sizing to the font's 1.2em line
        // box instead of the 0.8em of ink inside it leaves a third of the
        // screen blank.
        TextFitter.Fit fit = TextFitter.fit("HI", 10000, 600, 8, 4000, M);
        TextFitter.Block block = TextFitter.measure(fit.lines, fit.size, M);
        Assert.near("ink fills the box", 600, block.height, 1f);
    }

    private static void leadsMultipleLinesByTheFontButBoundsThemByTheirInk() {
        // Two lines span one 1.2em gap between baselines plus the 0.8em of ink
        // around them: 2.0em, not the 2.4em of two whole line boxes.
        TextFitter.Fit fit = TextFitter.fit("AA\nBB", 10000, 600, 8, 4000, M);
        Assert.near("two-line size", 300, fit.size, 1f);
    }

    private static void measuresABlockFromItsInk() {
        TextFitter.Block block = TextFitter.measure(
                TextFitter.wrap("AA\nBB", 10000, 100, M), 100, M);
        Assert.near("first baseline sits under the ink", 70, block.firstBaseline, 0.001f);
        Assert.near("ink height", 200, block.height, 0.001f);
    }

    private static void keepsBlankLinesHoldingTheirLineOpen() {
        // A leading newline draws nothing but must still push the text down.
        TextFitter.Block block = TextFitter.measure(
                TextFitter.wrap("\nBB", 10000, 100, M), 100, M);
        Assert.near("blank line keeps its height", 130, block.height, 0.001f);
        Assert.near("block starts at the blank baseline", 0, block.firstBaseline, 0.001f);
    }

    private static void wrapsOnSpaces() {
        Assert.lines("wraps", TextFitter.wrap("AA BB", 20, 10, M), "AA", "BB");
        Assert.lines("fits on one line", TextFitter.wrap("AA BB", 30, 10, M), "AA BB");
    }

    private static void keepsExplicitNewlines() {
        Assert.lines("newline", TextFitter.wrap("A\nB", 1000, 10, M), "A", "B");
        Assert.lines("blank line kept", TextFitter.wrap("A\n\nB", 1000, 10, M), "A", "", "B");
    }

    private static void breaksWordsTooLongToWrap() {
        // Four glyphs at 5px each exactly fill a 20px box.
        Assert.lines("char break", TextFitter.wrap("ABCDEFGH", 20, 10, M), "ABCD", "EFGH");
        Assert.lines("break after a wrap", TextFitter.wrap("XX ABCDEFGH", 20, 10, M),
                "XX", "ABCD", "EFGH");
    }

    private static void handlesEmptyText() {
        TextFitter.Fit fit = TextFitter.fit("", 100, 100, 8, 400, M);
        Assert.lines("one empty line", fit.lines, "");

        List<String> wrapped = TextFitter.wrap(null, 100, 10, M);
        Assert.lines("null wraps to one empty line", wrapped, "");
    }

    private static void survivesZeroSizedBox() {
        // Happens on the first layout pass, before the view has been measured.
        TextFitter.Fit fit = TextFitter.fit("HI", 0, 0, 8, 400, M);
        Assert.near("falls back to min size", 8, fit.size, 0.001f);
        Assert.lines("unwrapped", fit.lines, "HI");
    }
}
