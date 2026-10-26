package io.github.bluemapskinrestorerbridge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TexturePropertyDecoderTest {
    private final TexturePropertyDecoder decoder = new TexturePropertyDecoder();

    @Test
    void decodesAlternativeHostAndPreservesQuery() throws Exception {
        String value = encode("""
                {"profileName":"x","textures":{"SKIN":{"url":"https://skins.example.test/a.png?token=a%2Bb&v=2"}}}
                """);
        assertThat(decoder.decodeSkinUrl(value).toASCIIString())
                .isEqualTo("https://skins.example.test/a.png?token=a%2Bb&v=2");
    }

    @Test
    void rejectsMalformedBase64AndJson() {
        assertThatThrownBy(() -> decoder.decodeSkinUrl("%%%"))
                .isInstanceOf(IOException.class).hasMessageContaining("Base64");
        assertThatThrownBy(() -> decoder.decodeSkinUrl(encode("not-json")))
                .isInstanceOf(IOException.class).hasMessageContaining("malformed");
    }

    @Test
    void rejectsMissingOrEmptyUrlAndUnsupportedScheme() {
        assertThatThrownBy(() -> decoder.decodeSkinUrl(encode("{\"textures\":{\"SKIN\":{}}}")))
                .isInstanceOf(IOException.class).hasMessageContaining("no textures.SKIN.url");
        assertThatThrownBy(() -> decoder.decodeSkinUrl(encode("{\"textures\":{\"SKIN\":{\"url\":\"\"}}}")))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> decoder.decodeSkinUrl(encode("{\"textures\":{\"SKIN\":{\"url\":\"file:///skin.png\"}}}")))
                .isInstanceOf(IOException.class).hasMessageContaining("HTTP");
    }

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
