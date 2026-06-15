package com.katixo.hospital.notification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Tiny HTTP-JSON helper shared by notification providers (native JDK client, short timeout). */
final class HttpJson {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpJson() {
    }

    /** POSTs a JSON body with the given headers. Returns the raw response (2xx = success). */
    static HttpResponse<String> postJson(String url, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(b::header);
        return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Minimal JSON string escaping. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    /** India mobile normalisation to 91XXXXXXXXXX (digits only). */
    static String toIndianMsisdn(String mobile) {
        if (mobile == null) {
            return "";
        }
        String digits = mobile.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits;
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "91" + digits.substring(1);
        }
        return digits;
    }
}
