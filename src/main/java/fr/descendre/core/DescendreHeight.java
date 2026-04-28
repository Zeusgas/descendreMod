package fr.descendre.core;

public final class DescendreHeight {
    private DescendreHeight() {}

    public static boolean isInsideInternalRange(int y) {
        return y >= DescendreConstants.MIN_Y && y < DescendreConstants.MAX_Y_EXCLUSIVE;
    }

    public static boolean isInsideTargetRange(int y) {
        return y >= DescendreConstants.TARGET_MIN_Y && y <= DescendreConstants.TARGET_MAX_Y;
    }

    public static String internalRangeText() {
        return DescendreConstants.MIN_Y + " à " + (DescendreConstants.MAX_Y_EXCLUSIVE - 1);
    }

    public static String targetRangeText() {
        return DescendreConstants.TARGET_MIN_Y + " à " + DescendreConstants.TARGET_MAX_Y;
    }
}