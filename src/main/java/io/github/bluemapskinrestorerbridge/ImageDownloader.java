package io.github.bluemapskinrestorerbridge;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@FunctionalInterface
interface ImageDownloader {
    BufferedImage download(URI uri) throws IOException;

    final class Http implements ImageDownloader {
        private static final int MAX_REDIRECTS = 5;
        private final HttpClient client;
        private final Duration readTimeout;
        private final int maxBytes;
        private final int retries;
        private final long retryBackoffMillis;

        Http(Duration connectTimeout, Duration readTimeout, int maxBytes, int retries, long retryBackoffMillis) {
            this.client = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            this.readTimeout = readTimeout;
            this.maxBytes = maxBytes;
            this.retries = retries;
            this.retryBackoffMillis = retryBackoffMillis;
            ImageIO.setUseCache(false);
        }

        @Override
        public BufferedImage download(URI uri) throws IOException {
            IOException last = null;
            for (int attempt = 0; attempt <= retries; attempt++) {
                try {
                    return downloadOnce(uri);
                } catch (IOException e) {
                    last = e;
                    if (attempt < retries && retryBackoffMillis > 0) {
                        try {
                            Thread.sleep(Math.min(5000, retryBackoffMillis << attempt));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("skin download interrupted", interrupted);
                        }
                    }
                }
            }
            throw last;
        }

        private BufferedImage downloadOnce(URI initial) throws IOException {
            URI current = validate(initial);
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(readTimeout)
                        .header("Accept", "image/png,image/*;q=0.8")
                        .header("User-Agent", "BlueMap-SkinRestorer-Bridge/0.1.0")
                        .GET().build();
                final HttpResponse<InputStream> response;
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("skin download interrupted", e);
                }
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        String location = response.headers().firstValue("location")
                                .orElseThrow(() -> new IOException("redirect has no Location header"));
                        current = validate(current.resolve(location));
                        continue;
                    }
                    if (status != 200) throw new IOException("skin server returned HTTP " + status);
                    long announced = response.headers().firstValueAsLong("content-length").orElse(-1);
                    if (announced > maxBytes) throw new IOException("skin response exceeds configured size limit");
                    byte[] bytes = body.readNBytes(maxBytes + 1);
                    if (bytes.length > maxBytes) throw new IOException("skin response exceeds configured size limit");
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (image == null) throw new IOException("skin response is not a supported image");
                    if (image.getWidth() < 64 || image.getHeight() < 32 || image.getWidth() > 1024 || image.getHeight() > 1024) {
                        throw new IOException("skin image dimensions are outside 64x32..1024x1024");
                    }
                    return image;
                }
            }
            throw new IOException("too many skin URL redirects");
        }

        private static URI validate(URI uri) throws IOException {
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IOException("skin URL or redirect must use absolute HTTP/HTTPS");
            }
            return uri;
        }
    }
}
