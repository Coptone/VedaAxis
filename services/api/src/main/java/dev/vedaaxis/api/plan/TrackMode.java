package dev.vedaaxis.api.plan;

import java.util.List;
import java.util.Set;

public enum TrackMode {
    FOUR(List.of(TrackSlot.T1, TrackSlot.H1, TrackSlot.D1, TrackSlot.D2)),
    EIGHT(List.of(
            TrackSlot.MT, TrackSlot.ST, TrackSlot.H1, TrackSlot.H2,
            TrackSlot.D1, TrackSlot.D2, TrackSlot.D3, TrackSlot.D4));

    private final List<TrackSlot> orderedSlots;

    TrackMode(List<TrackSlot> orderedSlots) {
        this.orderedSlots = orderedSlots;
    }

    public List<TrackSlot> orderedSlots() {
        return orderedSlots;
    }

    public Set<TrackSlot> slots() {
        return Set.copyOf(orderedSlots);
    }
}
