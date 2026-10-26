package io.github.bluemapskinrestorerbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TexturePropertyDecoder {
    private static final int MAX_PROPERTY_BYTES = 64 * 1024;

    public URI decodeSkinUrl(String encoded) throws IOException {
        if (encoded == null || encoded.isBlank()) throw new IOException("empty texture property");
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IOException("texture property is not valid Base64", e);
        }
        if (decoded.length > MAX_PROPERTY_BYTES) throw new IOException("texture property exceeds 64 KiB");

        try {
            JsonElement root = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8));
            JsonObject textures = root.getAsJsonObject().getAsJsonObject("textures");
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            if (skin == null || !skin.has("url") || skin.get("url").getAsString().isBlank()) {
                throw new IOException("texture property has no textures.SKIN.url");
            }
            URI uri = URI.create(skin.get("url").getAsString());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IOException("skin URL must be absolute HTTP or HTTPS");
            }
            return uri;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("malformed texture property JSON", e);
        }
    }
}
