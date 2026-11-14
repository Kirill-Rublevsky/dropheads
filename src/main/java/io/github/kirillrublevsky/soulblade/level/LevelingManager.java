package io.github.kirillrublevsky.soulblade.level;

public final class LevelingManager {
    public static final int MAX_LEVEL = 50;
    public static final long KILLS_TO_MAX = 1_000_000L;
    private static final long[] THRESHOLDS = buildThresholds();

    private static long[] buildThresholds() {
        long[] values = new long[MAX_LEVEL + 1];
        for (int level = 1; level <= MAX_LEVEL; level++) {
            long n = level - 1L;
            values[level] = KILLS_TO_MAX * n * n / 2401L;
        }
        return values;
    }

    public int level(long kills) {
        long clamped = Math.max(0, kills);
        int low = 1, high = MAX_LEVEL;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (THRESHOLDS[middle] <= clamped) low = middle; else high = middle - 1;
        }
        return low;
    }

    public long threshold(int level) {
        if (level < 1 || level > MAX_LEVEL) throw new IllegalArgumentException("level must be 1..50");
        return THRESHOLDS[level];
    }

    public long remaining(long kills) {
        int level = level(kills);
        return level == MAX_LEVEL ? 0 : Math.max(0, THRESHOLDS[level + 1] - kills);
    }

    public double headChancePercent(int level) {
        return Math.min(15.0, Math.max(1, level) * 0.3);
    }
}
