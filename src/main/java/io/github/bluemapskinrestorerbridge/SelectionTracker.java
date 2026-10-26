package io.github.bluemapskinrestorerbridge;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class SelectionTracker {
    private final ConcurrentHashMap<UUID, Versioned> selections = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    Change observe(UUID playerId, SkinSelection selection) {
        final boolean[] changed = {false};
        Versioned value = selections.compute(playerId, (id, previous) -> {
            if (previous != null && previous.selection.fingerprint().equals(selection.fingerprint())
                    && previous.selection.kind() == selection.kind()) return previous;
            changed[0] = true;
            return new Versioned(sequence.incrementAndGet(), selection);
        });
        return new Change(value, changed[0]);
    }

    boolean isCurrent(UUID playerId, Versioned expected) {
        Versioned current = selections.get(playerId);
        return current != null && current.generation == expected.generation;
    }

    void remove(UUID playerId) { selections.remove(playerId); }
    void clear() { selections.clear(); }

    record Versioned(long generation, SkinSelection selection) {}
    record Change(Versioned value, boolean changed) {}
}
