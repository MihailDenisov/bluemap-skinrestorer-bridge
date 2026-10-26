package io.github.bluemapskinrestorerbridge;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTrackerTest {
    @Test
    void rejectsOlderGenerationAfterChangeAndClear() {
        UUID player = UUID.randomUUID();
        SelectionTracker tracker = new SelectionTracker();
        var first = tracker.observe(player, SkinSelection.valid(URI.create("https://a.test/1"), "mojang", "one"));
        assertThat(first.changed()).isTrue();
        assertThat(tracker.observe(player, first.value().selection()).changed()).isFalse();
        var second = tracker.observe(player, SkinSelection.valid(URI.create("https://b.test/2"), "ely.by", "two"));
        assertThat(tracker.isCurrent(player, first.value())).isFalse();
        assertThat(tracker.isCurrent(player, second.value())).isTrue();
        tracker.observe(player, SkinSelection.state(SkinSelection.Kind.CLEARED, "empty", "cleared", "clear"));
        assertThat(tracker.isCurrent(player, second.value())).isFalse();
    }
}
