package com.ruoyi.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TransportProxyHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TFNSW_BASE = "https://api.transport.nsw.gov.au";
    private static final HttpClient HTTP   = HttpClient.newHttpClient();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event,
                                                      Context ctx) {

        String apiKey = System.getenv("TFNSW_API_KEY");
        APIGatewayProxyResponseEvent out = new APIGatewayProxyResponseEvent();

        if (apiKey == null || apiKey.isBlank()) {
            return out.withStatusCode(500)
                    .withBody("{\"error\":\"TFNSW_API_KEY env var not set\"}");
        }

        try {
            /* === 拼 path + query === */
            String rawPath  = event.getPath() == null ? "" : event.getPath();
            String rawQuery = toQueryString(event.getQueryStringParameters());
            String target   = TFNSW_BASE + rawPath + rawQuery;

            /* === 调 TfNSW === */
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .header("Authorization", "apikey " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());

            /* === 构造响应 === */
            Map<String,String> hdr = new HashMap<>();
            hdr.put("Access-Control-Allow-Origin", "*");
            hdr.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
            res.headers()
                    .firstValue("content-type")
                    .ifPresent(ct -> hdr.put("Content-Type", ct));

            return out.withStatusCode(res.statusCode())
                    .withHeaders(hdr)
                    .withBody(new String(res.body()));   // TfNSW 都是 UTF-8 JSON

        } catch (Exception e) {
            ctx.getLogger().log("Proxy error: " + e.getMessage());
            return out.withStatusCode(502)
                    .withBody("{\"error\":\"proxy failed\"}");
        }
    }

    /** Map -> ?k=v&k2=v2 */
    private static String toQueryString(Map<String,String> params) {
        if (params == null || params.isEmpty()) return "";
        return "?" + params.entrySet().stream()
                .map(e -> url(e.getKey()) + "=" + url(e.getValue()))
                .collect(Collectors.joining("&"));
    }
    private static String url(String s){
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
