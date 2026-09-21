package com.doradux.tradesearchcycler;

/** Dependency-free regression tests; run by Gradle check. */
public final class RegressionTests {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int width : new int[]{320, 384, 400, 421, 422, 427, 480, 640, 854, 960, 1920}) {
            SearchLayout l = SearchLayout.of(width, 276);
            check(l.panelX() >= 0, "panel off left edge: " + width);
            check(l.panelX() + l.panelWidth() <= width, "panel off right edge: " + width);
            check(l.merchantX() >= 0 && l.merchantX() + 276 <= width, "merchant offscreen: " + width);
            if (!l.compact()) {
                check(l.panelX() + l.panelWidth() + 6 == l.merchantX(), "overlapping panels: " + width);
                check(l.panelWidth() >= 128, "unreadable sidebar: " + width);
            } else check(width < 422, "unnecessary compact mode");
        }
        CycleGate gate = new CycleGate();
        Object first = new Object();
        check(gate.ready(first), "first request must be allowed");
        gate.sent(first);
        check(gate.attempts() == 1, "count sent requests");
        for (int tick = 0; tick < 199; tick++) {
            check(!gate.ready(first), "must not resend while waiting");
            check(!gate.timedOut(first), "premature timeout");
            gate.tick();
        }
        gate.tick();
        check(gate.timedOut(first), "missing response must stop after 10 seconds");
        Object identicalRollResponse = new Object();
        check(gate.ready(identicalRollResponse), "an identical-content new packet acknowledges the roll");
        check(!gate.timedOut(identicalRollResponse), "received response must not time out");
        gate.sent(identicalRollResponse);
        Object nextResponse = new Object();
        for (int tick = 0; tick < CycleGate.MIN_TICKS; tick++) {
            check(!gate.ready(nextResponse), "minimum request spacing");
            gate.tick();
        }
        check(gate.ready(nextResponse), "response accepted after minimum spacing");
        check(gate.attempts() == 2, "attempt counter must only count sends");
        gate.restartCount();
        check(gate.attempts() == 0, "changing target starts its own counter");
        check(gate.pending(identicalRollResponse), "changing target retains an in-flight request");
        check(!gate.ready(identicalRollResponse), "changing target cannot send a duplicate request");
        check(!gate.pending(nextResponse), "a new response clears the pending barrier");
        gate.expire();
        check(!gate.pending(identicalRollResponse) && gate.ready(identicalRollResponse),
                "an expired request must allow an explicit manual retry");
        gate.reset();
        check(gate.attempts() == 0 && gate.ready(first), "new search resets all pending state");
        System.out.println("PASS: " + checks + " layout and cycle regression checks");
    }
}
