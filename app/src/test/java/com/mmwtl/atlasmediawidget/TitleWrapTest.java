package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TitleWrapTest {
    private static final TitleWrap.Measure CHARS = String::length;

    @Test public void fittingTitleStaysOnOneLine() {
        assertEquals("В сигаретном дыму", TitleWrap.pyramid("В сигаретном дыму", 20f, CHARS));
    }

    @Test public void longTitleBalancesWithLongerBottomLine() {
        assertEquals("Радио 7\nна семи холмах",
                TitleWrap.pyramid("Радио 7 на семи холмах", 16f, CHARS));
    }

    @Test public void prefersPyramidOverSlightlyMoreEvenTopHeavySplit() {
        assertEquals("abc\ndefg hijkl", TitleWrap.pyramid("abc defg hijkl", 10f, CHARS));
    }

    @Test public void shortWordMovesToTheBottomLine() {
        assertEquals("Песня\nо любви", TitleWrap.pyramid("Песня о любви", 10f, CHARS));
    }

    @Test public void keepsTextWhenNoTwoLineSplitFits() {
        assertEquals("aaaaaaaaaa bbbbbbbbbb", TitleWrap.pyramid("aaaaaaaaaa bbbbbbbbbb", 8f, CHARS));
        assertEquals("Oneverylongword", TitleWrap.pyramid("Oneverylongword", 5f, CHARS));
    }

    @Test public void topHeavySplitIsUsedOnlyWhenNoPyramidFits() {
        assertEquals("aaaaa\nbb", TitleWrap.pyramid("aaaaa bb", 6f, CHARS));
    }
}
