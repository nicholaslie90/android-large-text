package vc.east.bigsign;

final class SignStyleTest {

    static void run() {
        roundTripsDefaults();
        roundTripsCustomValues();
        keepsAwkwardText();
        stripsSeparatorsFromText();
        recoversFromGarbage();
        readsVersionOneRecords();
        copiesEveryField();
    }

    private static void roundTripsDefaults() {
        SignStyle s = new SignStyle();
        Assert.equal("default round trip", s, SignStyle.parse(s.serialize()));
    }

    private static void roundTripsCustomValues() {
        SignStyle s = new SignStyle();
        s.setText("PICK UP");
        s.fgColor = 0xFF00FF00;
        s.bgColor = 0xFF101010;
        s.bold = false;
        s.font = SignStyle.FONT_MONO;
        s.align = SignStyle.ALIGN_LEFT;
        s.marquee = true;
        s.speedDp = 350;

        SignStyle back = SignStyle.parse(s.serialize());
        Assert.equal("text", "PICK UP", back.text);
        Assert.equal("fg", 0xFF00FF00, back.fgColor);
        Assert.equal("bg", 0xFF101010, back.bgColor);
        Assert.isFalse("bold", back.bold);
        Assert.equal("font", SignStyle.FONT_MONO, back.font);
        Assert.equal("align", SignStyle.ALIGN_LEFT, back.align);
        Assert.isTrue("marquee", back.marquee);
        Assert.equal("speed", 350, back.speedDp);
    }

    private static void keepsAwkwardText() {
        String awkward = "Hello  world\nsecond line\ttab 中文 🚀 end";
        SignStyle s = new SignStyle();
        s.setText(awkward);
        Assert.equal("awkward text survives", awkward, SignStyle.parse(s.serialize()).text);
    }

    private static void stripsSeparatorsFromText() {
        SignStyle s = new SignStyle();
        s.setText("A" + SignStyle.FIELD + "B" + SignStyle.RECORD + "C");
        Assert.equal("separators become spaces", "A B C", s.text);

        SignStyle back = SignStyle.parse(s.serialize());
        Assert.equal("still parses", "A B C", back.text);
    }

    private static void recoversFromGarbage() {
        SignStyle fallback = new SignStyle();
        Assert.equal("null", fallback, SignStyle.parse(null));
        Assert.equal("empty", fallback, SignStyle.parse(""));
        Assert.equal("truncated", fallback, SignStyle.parse("1" + SignStyle.FIELD + "2"));

        String bad = new SignStyle().serialize()
                .replace(String.valueOf(new SignStyle().fgColor), "not-a-number");
        Assert.equal("non-numeric field", fallback, SignStyle.parse(bad));
    }

    private static void readsVersionOneRecords() {
        // Version 1 kept a hand-set size in field 1. The sign auto-fits now, so
        // the field is skipped — but the message and its colours must survive,
        // or upgrading would wipe the recents list.
        String v1 = join("1", "123.5", "-16711936", "-15724528", "0",
                String.valueOf(SignStyle.FONT_MONO),
                String.valueOf(SignStyle.ALIGN_LEFT), "1", "350", "PICK UP");

        SignStyle back = SignStyle.parse(v1);
        Assert.equal("text", "PICK UP", back.text);
        Assert.equal("fg", 0xFF00FF00, back.fgColor);
        Assert.equal("bg", 0xFF101010, back.bgColor);
        Assert.isFalse("bold", back.bold);
        Assert.equal("font", SignStyle.FONT_MONO, back.font);
        Assert.equal("align", SignStyle.ALIGN_LEFT, back.align);
        Assert.isTrue("marquee", back.marquee);
        Assert.equal("speed", 350, back.speedDp);

        // Re-saving drops the stale field rather than carrying it forever.
        Assert.equal("rewritten as version 2", back, SignStyle.parse(back.serialize()));
    }

    private static String join(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(SignStyle.FIELD);
            sb.append(fields[i]);
        }
        return sb.toString();
    }

    private static void copiesEveryField() {
        SignStyle s = new SignStyle();
        s.setText("ORIGINAL");
        s.marquee = true;
        s.speedDp = 42;

        SignStyle copy = new SignStyle(s);
        Assert.equal("copy equals source", s, copy);

        copy.setText("CHANGED");
        Assert.equal("copy is independent", "ORIGINAL", s.text);
    }
}
