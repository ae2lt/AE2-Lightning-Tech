package com.moakiee.ae2lt.client;

public final class PatternProviderPageScroll {
    public enum Direction { PREVIOUS, NEXT, NONE }

    private PatternProviderPageScroll() {
    }

    public static Direction directionForDelta(double scrollY) {
        if (scrollY > 0) {
            return Direction.PREVIOUS;
        }
        if (scrollY < 0) {
            return Direction.NEXT;
        }
        return Direction.NONE;
    }
}
