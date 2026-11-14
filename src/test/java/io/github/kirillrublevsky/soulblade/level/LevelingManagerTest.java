package io.github.kirillrublevsky.soulblade.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelingManagerTest {
    private final LevelingManager levels = new LevelingManager();
    @Test void exactThresholds(){assertEquals(416,levels.threshold(2));assertEquals(33_735,levels.threshold(10));assertEquals(239_900,levels.threshold(25));assertEquals(959_600,levels.threshold(49));assertEquals(1_000_000,levels.threshold(50));}
    @Test void levelBoundaries(){assertEquals(1,levels.level(415));assertEquals(2,levels.level(416));assertEquals(49,levels.level(999_999));assertEquals(50,levels.level(1_000_000));assertEquals(50,levels.level(Long.MAX_VALUE));}
    @Test void chanceIsCapped(){assertEquals(0.3,levels.headChancePercent(1));assertEquals(15.0,levels.headChancePercent(50));assertEquals(15.0,levels.headChancePercent(500));}
}
