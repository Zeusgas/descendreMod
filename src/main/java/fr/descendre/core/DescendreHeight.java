package fr.descendre.core;

public final class DescendreHeight {

    public static final int TARGET_MIN_Y = -15000;
    public static final int TARGET_MAX_Y = 5000;

    public static final int INTERNAL_MIN_Y = -15008;
    public static final int INTERNAL_MAX_Y = 5007;

    private DescendreHeight() {}

    public static boolean isInsideTargetRange(int y) {
        return y >= TARGET_MIN_Y && y <= TARGET_MAX_Y;
    }

    public static boolean isInsideInternalRange(int y) {
        return y >= INTERNAL_MIN_Y && y <= INTERNAL_MAX_Y;
    }

    public static String targetRangeText() {
        return TARGET_MIN_Y + " à " + TARGET_MAX_Y;
    }

    public static String internalRangeText() {
        return INTERNAL_MIN_Y + " à " + INTERNAL_MAX_Y;
    }
}