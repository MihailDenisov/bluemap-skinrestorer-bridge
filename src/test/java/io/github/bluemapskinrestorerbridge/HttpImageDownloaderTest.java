package io.github.bluemapskinrestorerbridge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpImageDownloaderTest {
    private HttpServer server;
    private URI base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach void stop() { server.stop(0); }

    @Test
    void followsValidatedRedirectAndPreservesQuery() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        byte[] png = png(64, 64);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/skin?signature=a%2Bb&v=2");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/skin", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png); exchange.close();
        });
        BufferedImage result = downloader(4096).download(base.resolve("/redirect"));
        assertThat(result.getWidth()).isEqualTo(64);
        assertThat(query).hasValue("signature=a%2Bb&v=2");
    }

    @Test
    void rejectsInvalidOversizedAndWrongDimensionResponses() throws Exception {
        server.createContext("/invalid", exchange -> {
            byte[] bytes = "not-image".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/oversized", exchange -> {
            byte[] bytes = new byte[1025];
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        byte[] tiny = png(1, 1);
        server.createContext("/tiny", exchange -> {
            exchange.sendResponseHeaders(200, tiny.length);
            exchange.getResponseBody().write(tiny); exchange.close();
        });
        assertThatThrownBy(() -> downloader(4096).download(base.resolve("/invalid")))
                .isInstanceOf(IOException.class).hasMessageContaining("supported image");
        assertThatThrownBy(() -> downloader(1024).download(base.resolve("/oversized")))
                .isInstanceOf(IOException.class).hasMessageContaining("size limit");
        assertThatThrownBy(() -> downloader(4096).download(base.resolve("/tiny")))
                .isInstanceOf(IOException.class).hasMessageContaining("dimensions");
    }

    private static ImageDownloader.Http downloader(int maxBytes) {
        return new ImageDownloader.Http(Duration.ofSeconds(2), Duration.ofSeconds(2), maxBytes, 0, 0);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }
}
