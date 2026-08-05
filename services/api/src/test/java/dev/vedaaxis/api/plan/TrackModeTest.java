package dev.vedaaxis.api.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackModeTest {
    @Test
    void fourPlayerModeHasCanonicalSlots() {
        assertThat(TrackMode.FOUR.orderedSlots())
                .containsExactly(TrackSlot.T1, TrackSlot.H1, TrackSlot.D1, TrackSlot.D2);
    }

    @Test
    void eightPlayerModeHasCanonicalSlots() {
        assertThat(TrackMode.EIGHT.orderedSlots()).containsExactlyElementsOf(List.of(
                TrackSlot.MT, TrackSlot.ST, TrackSlot.H1, TrackSlot.H2,
                TrackSlot.D1, TrackSlot.D2, TrackSlot.D3, TrackSlot.D4));
    }
}
