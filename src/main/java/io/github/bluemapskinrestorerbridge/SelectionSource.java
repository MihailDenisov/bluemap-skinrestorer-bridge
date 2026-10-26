package io.github.bluemapskinrestorerbridge;

import java.util.UUID;

@FunctionalInterface
interface SelectionSource {
    SkinSelection read(UUID playerId);
}
