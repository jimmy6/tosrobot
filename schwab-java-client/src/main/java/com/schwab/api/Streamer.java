package com.schwab.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Streamer extends WebSocketClient {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final CountDownLatch loginLatch = new CountDownLatch(1);

    private String accessToken;
    private String schwabClientChannel;
    private String schwabClientFunctionId;
    private String schwabClientCustomerId;

    public Streamer(URI serverUri, String accessToken, String channel, String functionId, String customerId) {
        super(serverUri);
        this.accessToken = accessToken;
        this.schwabClientChannel = channel;
        this.schwabClientFunctionId = functionId;
        this.schwabClientCustomerId = customerId;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[Streamer] Connected. Sending Login...");
        sendLogin();
    }

    private void sendLogin() {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("Authorization", accessToken);
            params.put("SchwabClientChannel", schwabClientChannel);
            params.put("SchwabClientFunctionId", schwabClientFunctionId);

            Map<String, Object> req = new HashMap<>();
            req.put("service", "ADMIN");
            req.put("requestid", "1");
            req.put("command", "LOGIN");
            req.put("SchwabClientCustomerId", schwabClientCustomerId);
            req.put("parameters", params);

            String json = mapper.writeValueAsString(req);
            this.send(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void subscribeLevel1(String symbols) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("keys", symbols);
            params.put("fields", "0,1,2,3"); // Symbol, Bid, Ask, Last

            String service = "LEVELONE_EQUITIES";
            if (symbols.contains("/")) {
                service = "LEVELONE_FUTURES";
            }

            Map<String, Object> req = new HashMap<>();
            req.put("service", service);
            req.put("requestid", "3");
            req.put("command", "SUBS");
            req.put("parameters", params);

            String json = mapper.writeValueAsString(req);
            System.out.println("[Streamer] Subscribing to " + service + ": " + json);
            this.send(json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = mapper.readTree(message);

            if (root.has("response")) {
                for (JsonNode resp : root.get("response")) {
                    String service = resp.path("service").asText();
                    String command = resp.path("command").asText();

                    if ("ADMIN".equals(service) && "LOGIN".equals(command)) {
                        String content = resp.path("content").path("code").asText();
                        if ("0".equals(content)) {
                            System.out.println("[Streamer] Login Successful! Ready.");
                            loginLatch.countDown();
                        } else {
                            System.out.println("[Streamer] Login Failed: " + resp.toPrettyString());
                        }
                    }

                    if ("ADMIN".equals(service) && "QOS".equals(command)) {
                        String content = resp.path("content").path("code").asText();
                        if ("0".equals(content)) {
                            System.out.println("[Streamer] QOS Set! Ready to Subscribe.");
                            loginLatch.countDown();
                        }
                    }
                }
            }

            if (root.has("data")) {
                for (JsonNode data : root.get("data")) {
                    String service = data.path("service").asText();
                    if ("LEVELONE_EQUITIES".equals(service) || "LEVELONE_FUTURES".equals(service)) {
                        JsonNode content = data.path("content");
                        if (content.isArray()) {
                            for (JsonNode item : content) {
                                String key = item.path("key").asText();
                                double bid = item.path("1").asDouble();
                                double ask = item.path("2").asDouble();
                                double last = item.path("3").asDouble();

                                System.out.println(String.format("STREAM | %s | Last: %.2f | Bid: %.2f | Ask: %.2f",
                                        key, last, bid, ask));
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[Streamer] Closed: Code " + code + ", Reason: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("[Streamer] Error: " + ex.getMessage());
    }

    public void waitForLogin() throws InterruptedException {
        loginLatch.await(10, TimeUnit.SECONDS);
    }
}