package com.doradux.tradesearchcycler;

/** One request in flight. An unchanged roll is still acknowledged by a new offers packet. */
final class CycleGate {
    static final int MIN_TICKS = 4;
    static final int TIMEOUT_TICKS = 200;
    private Object sentOffers;
    private int elapsed;
    private boolean waiting;
    private int attempts;

    void reset() { sentOffers = null; elapsed = 0; waiting = false; attempts = 0; }
    // Changing the target cannot recall an already transmitted request.
    void restartCount() { attempts = 0; }
    void expire() { sentOffers = null; waiting = false; }
    void sent(Object offers) { sentOffers = offers; elapsed = 0; waiting = true; attempts++; }
    void tick() { if (waiting) elapsed++; }
    boolean ready(Object offers) { return !waiting || (offers != sentOffers && elapsed >= MIN_TICKS); }
    boolean pending(Object offers) { return waiting && offers == sentOffers; }
    boolean timedOut(Object offers) { return waiting && offers == sentOffers && elapsed >= TIMEOUT_TICKS; }
    int attempts() { return attempts; }
}
