package fr.descendre.core;

public final class DescendreConstants {
    private DescendreConstants() {}

    public static final int CUBE_SIZE = 16;
    public static final int CUBE_VOLUME = CUBE_SIZE * CUBE_SIZE * CUBE_SIZE;

    // Objectif joueur : -15000 à +5000
    public static final int TARGET_MIN_Y = -15000;
    public static final int TARGET_MAX_Y = 5000;

    // Limites internes alignées sur 16
    public static final int MIN_Y = -15008;
    public static final int MAX_Y_EXCLUSIVE = 5008;
    public static final int HEIGHT = MAX_Y_EXCLUSIVE - MIN_Y;
}