package fr.descendre.cubic;

public final class DescendreCubicMode {

    private static boolean enabled = true;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    private DescendreCubicMode() {
    }
}