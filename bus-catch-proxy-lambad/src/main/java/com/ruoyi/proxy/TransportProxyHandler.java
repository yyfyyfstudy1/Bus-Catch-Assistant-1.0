package com.ruoyi.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class TransportProxyHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String TFNSW_BASE = "https://api.transport.nsw.gov.au";
    private static final HttpClient HTTP   = HttpClient.newHttpClient();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context ctx) {

        /* ---------- 0. 读取 apikey ---------- */
        String apiKey = System.getenv("TFNSW_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return error(500, "TFNSW_API_KEY env var not set");
        }

        try {
            /* ---------- 1. 还原目标 URL ---------- */
            String rawPath  = event.getRawPath();          // 例如 /v1/tp/trip
            String rawQuery = event.getRawQueryString();   // param1=x&param2=y
            if (rawQuery != null && !rawQuery.isEmpty()) rawQuery = "?" + rawQuery;

            String target = TFNSW_BASE + rawPath + (rawQuery == null ? "" : rawQuery);

            /* ---------- 2. 请求 TfNSW ---------- */
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .header("Authorization", "apikey " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());

            /* ---------- 3. 构造回包（不再重复加 CORS 头） ---------- */
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(res.statusCode())
                    .withHeaders(Map.of(
                            "Content-Type", res.headers()
                                    .firstValue("content-type")
                                    .orElse("application/json")
                    ))
                    .withIsBase64Encoded(false)
                    .withBody(new String(res.body()))
                    .build();

        } catch (Exception ex) {
            ctx.getLogger().log("Proxy error: " + ex.getMessage());
            return error(502, "proxy failed");
        }
    }

    /* ---------- 小工具：统一错误响应（同样不重复加 CORS 头） ---------- */
    private APIGatewayV2HTTPResponse error(int code, String msg) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(code)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody("{\"error\":\"" + msg + "\"}")
                .build();
    }
}
