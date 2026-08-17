package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CardSuppressionPolicyTest {
    @Test
    public void keepsCardAllowedUntilActivityLeavesHome() {
        CardSuppressionPolicy policy = new CardSuppressionPolicy();

        policy.suppress(100L, 1_500L);

        assertFalse(policy.isCardAllowed(200L));
        policy.onVisibility(true, 200L);
        assertFalse(policy.isCardAllowed(300L));
        policy.onVisibility(false, 400L);
        assertFalse(policy.isCardAllowed(500L));
        policy.onVisibility(true, 600L);
        assertTrue(policy.isCardAllowed(601L));
        assertEquals(CardSuppressionPolicy.State.IDLE, policy.state());
    }

    @Test
    public void suppressionExpiresWhenNoActivityTakesOver() {
        CardSuppressionPolicy policy = new CardSuppressionPolicy();

        policy.suppress(100L, 1_500L);

        assertFalse(policy.isCardAllowed(1_599L));
        assertTrue(policy.isCardAllowed(1_600L));
        assertEquals(CardSuppressionPolicy.State.IDLE, policy.state());
    }
}
