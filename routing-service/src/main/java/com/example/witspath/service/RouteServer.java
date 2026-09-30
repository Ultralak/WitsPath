package com.example.witspath.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** Cloud Run entry point. Listens on $PORT (default 8080). */
public final class RouteServer {
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/healthz", ex -> send(ex, 200, "{\"ok\":true}"));
        server.createContext("/v1/route", RouteServer::route);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("routing-service listening on " + port);
    }

    private static void route(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, Object> response;
        int status = 200;
        try {
            String body = read(ex.getRequestBody());
            response = RouteHandler.handle(Json.parseObject(body));
            if (Boolean.FALSE.equals(response.get("ok")) && "bad_request".equals(response.get("error"))) status = 400;
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            response = new LinkedHashMap<>();
            response.put("ok", false);
            response.put("error", "bad_request");
            response.put("message", "Request body is not valid JSON");
            status = 400;
        } catch (RuntimeException e) {
            response = new LinkedHashMap<>();
            response.put("ok", false);
            response.put("error", "internal");
            response.put("message", "Routing failed");
            status = 500;
        }
        send(ex, status, Json.write(response));
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BODY_BYTES) throw new IllegalArgumentException("Body too large");
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
