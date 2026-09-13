package vc.east.bigsign;

import java.util.List;

final class HistoryTest {

    static void run() {
        addsNewestFirst();
        ignoresBlankText();
        promotesAndUpdatesDuplicates();
        capsAtMax();
        removesByIndex();
        roundTrips();
        skipsBadRecordsOnParse();
        handsOutDefensiveCopies();
    }

    private static History of(String... texts) {
        History h = new History();
        for (String t : texts) {
            h.add(styled(t));
        }
        return h;
    }

    private static SignStyle styled(String text) {
        SignStyle s = new SignStyle();
        s.setText(text);
        return s;
    }

    private static void addsNewestFirst() {
        History h = of("FIRST", "SECOND");
        Assert.equal("size", 2, h.size());
        Assert.equal("newest at 0", "SECOND", h.get(0).text);
        Assert.equal("oldest at 1", "FIRST", h.get(1).text);
    }

    private static void ignoresBlankText() {
        History h = of("", "   ", "\n");
        h.add(null);
        Assert.isTrue("blank text is never stored", h.isEmpty());
    }

    private static void promotesAndUpdatesDuplicates() {
        History h = of("A", "B");
        SignStyle updated = styled("A");
        updated.fgColor = 0xFFFF0000;
        h.add(updated);

        Assert.equal("no duplicate", 2, h.size());
        Assert.equal("promoted to front", "A", h.get(0).text);
        Assert.equal("styling updated", 0xFFFF0000, h.get(0).fgColor);
    }

    private static void capsAtMax() {
        History h = new History();
        for (int i = 0; i < History.MAX + 5; i++) {
            h.add(styled("entry " + i));
        }
        Assert.equal("capped", History.MAX, h.size());
        Assert.equal("newest kept", "entry 24", h.get(0).text);
        Assert.equal("oldest dropped", "entry 5", h.get(History.MAX - 1).text);
    }

    private static void removesByIndex() {
        History h = of("A", "B", "C");
        h.remove(1);
        Assert.equal("size after remove", 2, h.size());
        Assert.equal("remaining newest", "C", h.get(0).text);
        Assert.equal("remaining oldest", "A", h.get(1).text);

        h.remove(-1);
        h.remove(99);
        Assert.equal("out of range is a no-op", 2, h.size());

        h.clear();
        Assert.isTrue("cleared", h.isEmpty());
    }

    private static void roundTrips() {
        History h = of("ONE", "TWO with spaces", "THREE\nwith newline");
        History back = History.parse(h.serialize());

        Assert.equal("size preserved", h.size(), back.size());
        for (int i = 0; i < h.size(); i++) {
            Assert.equal("entry " + i, h.get(i), back.get(i));
        }
        Assert.isTrue("empty round trip", History.parse(new History().serialize()).isEmpty());
        Assert.isTrue("null parses", History.parse(null).isEmpty());
    }

    private static void skipsBadRecordsOnParse() {
        String serialized = of("GOOD").serialize()
                + SignStyle.RECORD + "corrupt"
                + SignStyle.RECORD + of("ALSO GOOD").serialize();
        History back = History.parse(serialized);
        Assert.equal("bad record skipped", 2, back.size());
        Assert.equal("first survivor", "GOOD", back.get(0).text);
        Assert.equal("second survivor", "ALSO GOOD", back.get(1).text);
    }

    private static void handsOutDefensiveCopies() {
        History h = of("A");
        List<SignStyle> snapshot = h.entries();
        snapshot.clear();
        Assert.equal("history unaffected by caller", 1, h.size());
    }
}
