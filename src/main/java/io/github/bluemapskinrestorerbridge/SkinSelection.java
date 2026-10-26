package io.github.bluemapskinrestorerbridge;

import java.net.URI;

record SkinSelection(Kind kind, URI textureUrl, String provider, String fingerprint, String diagnostic) {
    enum Kind { VALID, NO_SELECTION, CLEARED, INVALID }

    static SkinSelection valid(URI url, String provider, String fingerprint) {
        return new SkinSelection(Kind.VALID, url, provider, fingerprint, "");
    }

    static SkinSelection state(Kind kind, String provider, String fingerprint, String diagnostic) {
        return new SkinSelection(kind, null, provider, fingerprint, diagnostic);
    }
}
