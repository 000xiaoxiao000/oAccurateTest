package com.oAT.relay;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RelayController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    private final RelayProperties properties;
    private final HttpClient httpClient;

    public RelayController(RelayProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> forward(
            HttpServletRequest servletRequest,
            @RequestBody(required = false) byte[] body
    ) throws IOException, InterruptedException {
        byte[] requestBody = body == null ? new byte[0] : body;
        int maxBodyBytes = properties.getMaxBodySizeMb() * 1024 * 1024;
        if (requestBody.length > maxBodyBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "request body exceeds relay limit");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(buildTargetUri(servletRequest))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        copyRequestHeaders(servletRequest, builder);
        if (!properties.getAuthToken().isBlank()) {
            builder.header("Authorization", properties.getAuthToken());
        }

        String method = servletRequest.getMethod();
        HttpRequest.BodyPublisher publisher = requestBody.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(requestBody);
        builder.method(method, publisher);

        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpHeaders responseHeaders = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                responseHeaders.put(name, values);
            }
        });

        return ResponseEntity.status(response.statusCode())
                .headers(responseHeaders)
                .body(response.body());
    }

    private URI buildTargetUri(HttpServletRequest request) {
        String baseUrl = properties.getTargetBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = request.getRequestURI();
        String query = request.getQueryString();
        return URI.create(normalizedBase + path + (query == null || query.isBlank() ? "" : "?" + query));
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                builder.header(name, values.nextElement());
            }
        }
    }
}
