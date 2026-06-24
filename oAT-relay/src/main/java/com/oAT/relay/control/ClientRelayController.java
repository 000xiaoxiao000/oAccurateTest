package com.oAT.relay.control;

import com.oAT.relay.model.RelayRequest;
import com.oAT.relay.service.RelayRateLimiter;
import com.oAT.relay.service.RelayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/client")
public class ClientRelayController {
    private final RelayService relayService;
    private final RelayRateLimiter rateLimiter;

    public ClientRelayController(RelayService relayService, RelayRateLimiter rateLimiter) {
        this.relayService = relayService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam MultiValueMap<String, String> params,
                                        @RequestHeader HttpHeaders headers) {
        return forwardForm("/client/login", params, headers);
    }

    @PostMapping("/heartbeat/{sessionId}/{appId}/{timesTamp}")
    public ResponseEntity<String> heartbeat(@PathVariable String sessionId,
                                            @PathVariable String appId,
                                            @PathVariable Long timesTamp,
                                            @RequestHeader HttpHeaders headers) {
        String path = "/client/heartbeat/" + encode(sessionId) + "/" + encode(appId) + "/" + timesTamp;
        return forwardForm(path, new LinkedMultiValueMap<>(), headers);
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam MultiValueMap<String, String> params,
                                         @RequestHeader HttpHeaders headers) {
        return forwardForm("/client/upload", params, headers);
    }

    @PostMapping("/coverage/upload")
    public ResponseEntity<String> uploadCoverage(@RequestParam MultiValueMap<String, String> params,
                                                 @RequestHeader HttpHeaders headers) {
        return forwardForm("/client/coverage/upload", params, headers);
    }

    @PostMapping("/uploadStaticData")
    public ResponseEntity<String> uploadStaticData(@RequestParam Map<String, String> params,
                                                   @RequestBody(required = false) String body,
                                                   @RequestHeader HttpHeaders headers) {
        return forwardBody(appendQuery("/client/uploadStaticData", params), body, headers);
    }

    @PostMapping("/packageVerify")
    public ResponseEntity<String> packageVerify(@RequestParam MultiValueMap<String, String> params,
                                                @RequestHeader HttpHeaders headers) {
        return forwardForm("/client/packageVerify", params, headers);
    }

    @PostMapping("/sandbox/status")
    public ResponseEntity<String> sandboxStatus(@RequestParam MultiValueMap<String, String> params,
                                                @RequestHeader HttpHeaders headers) {
        return forwardForm("/client/sandbox/status", params, headers);
    }

    @PostMapping("/**")
    public ResponseEntity<String> fallback(HttpServletRequest request,
                                           @RequestParam(required = false) MultiValueMap<String, String> params,
                                           @RequestBody(required = false) String body,
                                           @RequestHeader HttpHeaders headers) {
        String path = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            path = path + "?" + request.getQueryString();
        }
        if (body != null) {
            return forwardBody(path, body, headers);
        }
        return forwardForm(path, params == null ? new LinkedMultiValueMap<>() : params, headers);
    }

    private ResponseEntity<String> forwardForm(String path, MultiValueMap<String, String> params, HttpHeaders headers) {
        ResponseEntity<String> rejected = rejectIfNecessary(params, null, headers);
        if (rejected != null) {
            return rejected;
        }
        return relayService.handle(new RelayRequest(HttpMethod.POST, path, params, null, headers));
    }

    private ResponseEntity<String> forwardBody(String path, String body, HttpHeaders headers) {
        ResponseEntity<String> rejected = rejectIfNecessary(null, body, headers);
        if (rejected != null) {
            return rejected;
        }
        return relayService.handle(new RelayRequest(HttpMethod.POST, path, null, body, headers));
    }

    private ResponseEntity<String> rejectIfNecessary(MultiValueMap<String, String> params, String body, HttpHeaders headers) {
        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("rate limit exceeded");
        }
        if (!relayService.isAuthorized(headers.getFirst("X-OAT-Relay-Token"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }
        if (relayService.isPayloadTooLarge(params, body)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("payload too large");
        }
        return null;
    }

    private String appendQuery(String path, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return path;
        }
        StringBuilder builder = new StringBuilder(path).append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    private String encode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
