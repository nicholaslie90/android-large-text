package vc.east.bigsign;

import java.util.List;

/** Minimal assertions, so the tests need no third-party jar. */
final class Assert {

    private Assert() {
    }

    static void isTrue(String what, boolean value) {
        if (!value) throw new AssertionError(what + ": expected true");
    }

    static void isFalse(String what, boolean value) {
        if (value) throw new AssertionError(what + ": expected false");
    }

    static void equal(String what, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void equal(String what, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void near(String what, float expected, float actual, float tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(what + ": expected <" + expected + "> +/- " + tolerance
                    + " but was <" + actual + ">");
        }
    }

    static void lines(String what, List<String> actual, String... expected) {
        equal(what + " line count " + actual, expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            equal(what + " line " + i, expected[i], actual.get(i));
        }
    }
}
