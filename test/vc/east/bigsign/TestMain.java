package vc.east.bigsign;

/** Host-JVM test runner for the parts of the app that carry no Android types. */
public final class TestMain {

    public static void main(String[] args) {
        int failures = 0;
        failures += run("SignStyle", SignStyleTest::run);
        failures += run("History", HistoryTest::run);
        failures += run("TextFitter", TextFitterTest::run);

        if (failures > 0) {
            System.out.println("\n" + failures + " suite(s) FAILED");
            System.exit(1);
        }
        System.out.println("\nAll suites passed.");
    }

    private static int run(String name, Runnable suite) {
        try {
            suite.run();
            System.out.println("PASS  " + name);
            return 0;
        } catch (AssertionError | RuntimeException e) {
            System.out.println("FAIL  " + name + ": " + e.getMessage());
            return 1;
        }
    }
}
