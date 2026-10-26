package io.github.bluemapskinrestorerbridge;

import com.mojang.authlib.properties.Property;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.skin.SkinValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class SkinRestorerSelectionSource implements SelectionSource {
    private final TexturePropertyDecoder decoder = new TexturePropertyDecoder();
    private final Object storageReadLock = new Object();

    public SkinSelection read(UUID playerId) {
        final SkinValue selected;
        // SkinStorage uses a concurrent map, but a cold lookup performs synchronous SkinIO work.
        // Keep that work off the server thread and serialize it because SkinIO has no thread-safety contract.
        synchronized (storageReadLock) {
            selected = SkinRestorer.getSkinStorage().getSkin(playerId);
        }
        if (selected == null) {
            return SkinSelection.state(SkinSelection.Kind.NO_SELECTION, "none", "none", "no saved selection");
        }
        String provider = selected.provider() == null ? "unknown" : selected.provider();
        Property property = selected.value();
        if (selected == SkinValue.EMPTY || property == null || property.value() == null || property.value().isBlank()) {
            return SkinSelection.state(SkinSelection.Kind.CLEARED, provider, "cleared", "explicit empty selection");
        }
        String fingerprint = fingerprint(property.value());
        try {
            return SkinSelection.valid(decoder.decodeSkinUrl(property.value()), provider, fingerprint);
        } catch (IOException e) {
            return SkinSelection.state(SkinSelection.Kind.INVALID, provider, fingerprint, e.getMessage());
        }
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
