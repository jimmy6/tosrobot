package com.schwab.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.HttpUrl;
import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.concurrent.TimeUnit;

public class TelegramBot implements Runnable {

    private static final String TOKEN = System.getenv("TELEGRAM_BOT_TOKEN");
    private static final String CHAT_ID = System.getenv("TELEGRAM_CHAT_ID");
    private static final String API_URL = "https://api.telegram.org/bot" + (TOKEN != null ? TOKEN : "");
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(35, TimeUnit.SECONDS)
            .build();

    // We store the last update ID to acknowledge messages and not re-process them.
    private long lastUpdateId = 0;

    // Store the exact unix timestamp (in seconds) when this bot thread started.
    // Telegram API sends message dates in Unix Seconds.
    private final long bootTime = System.currentTimeMillis() / 1000;

    @Override
    public void run() {
        System.out.println("  [TelegramBot] Background Polling Thread Started.");

        File rebootFlag = new File(".reboot_flag");
        if (rebootFlag.exists()) {
            rebootFlag.delete();
            System.out.println("  [TelegramBot] Reboot flag detected! Scheduling auto-status Check in 60 seconds...");
            new Thread(() -> {
                try {
                    Thread.sleep(60000);
                    System.out.println("  [TelegramBot] Executing scheduled auto-status...");
                    processCommand("/status");
                } catch (Exception e) {
                }
            }).start();
        }

        while (true) {
            try {
                // Long Polling: The server will wait up to 30 seconds before returning empty,
                // saving CPU.
                HttpUrl url = HttpUrl.parse(API_URL + "/getUpdates").newBuilder()
                        .addQueryParameter("offset", String.valueOf(lastUpdateId))
                        .addQueryParameter("timeout", "30")
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String json = response.body().string();
                        JsonNode root = mapper.readTree(json);
                        JsonNode result = root.path("result");

                        if (result.isArray() && result.size() > 0) {
                            for (JsonNode update : result) {
                                long updateId = update.path("update_id").asLong();
                                lastUpdateId = updateId + 1; // Acknowledge message

                                JsonNode message = update.path("message");
                                if (message.has("date")) {
                                    long messageDate = message.path("date").asLong();

                                    // SECURITY: Ignore any messages sent BEFORE the bot booted up
                                    // This prevents the bot from executing an infinite backlog loop of restarts
                                    if (messageDate < bootTime) {
                                        System.out
                                                .println("  [TelegramBot] Ignoring stale message from " + messageDate);
                                        continue;
                                    }
                                }

                                if (message.has("text")) {
                                    String text = message.path("text").asText().trim().toLowerCase();
                                    System.out.println("  [TelegramBot] Received command: " + text);
                                    processCommand(text);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("  [TelegramBot] Polling Error: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private void processCommand(String command) {
        if (command.equals("/status")) {
            System.out.println("  [TelegramBot] Executing /status subroutine...");
            long lastTime = App.getLastCandleTime();
            long now = System.currentTimeMillis();

            // Calculate delay in minutes
            long diffMs = now - lastTime;
            long diffMins = diffMs / 60000;

            // OS-Level Process Counting
            int javaCount = 0;
            try {
                Process p = Runtime.getRuntime().exec("tasklist /FI \"IMAGENAME eq java.exe\" /NH");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("java.exe")) {
                        javaCount++;
                    }
                }
            } catch (Exception e) {
                System.out.println("  [TelegramBot] Failed to count java instances: " + e.getMessage());
            }

            String statusPayload;
            if (lastTime == -1) {
                statusPayload = "⚠️ STATUS: Initializing (No candles processed yet)";
            } else if (diffMins <= 1) {
                statusPayload = "✅ STATUS: Synced (Active current minute)";
            } else {
                statusPayload = "❌ STATUS: Delayed (" + diffMins + " minutes behind)";
            }

            if (javaCount == 1) {
                statusPayload += "\n✅ Active Java Engines: " + javaCount;
            } else {
                statusPayload += "\n❌ Active Java Engines: " + javaCount + " (WARNING: Duplicate Detected)";
            }

            // Take screenshot and send
            TradeExecutor.executeStatusCheck(statusPayload);

        } else if (command.equals("/restart")) {
            System.out.println("  [TelegramBot] Executing /restart subroutine...");

            // Send acknowledgement first
            sendMessage("🔄 RESTART COMMAND RECEIVED. Terminating TOS and rebooting Java Engine...");

            try {
                // Drop a reboot flag so the next instance knows to send an auto-status Check
                new File(".reboot_flag").createNewFile();

                // 1) Kill ThinkOrSwim violently
                Runtime.getRuntime().exec("taskkill /F /IM thinkorswim.exe");

                // 2) Spawn a detached asynchronous watchdog CMD to wait 5 seconds and then
                // launch the Master Script.
                // We use cmd /c start to fully detach it from the Java process tree so it
                // survives our System.exit(0).
                String ps1Path = "C:\\Users\\WDAGUtilityAccount\\Desktop\\robot\\Launch-TradingRobot.ps1";
                String watchdogCmd = "cmd /c start cmd /c \"timeout /t 5 /nobreak >nul & powershell -ExecutionPolicy Bypass -File "
                        + ps1Path + "\"";
                Runtime.getRuntime().exec(watchdogCmd);

                // 3) Terminate this current Java instance entirely.
                System.out.println("  [TelegramBot] Spawning Detached Watchdog Toolkit. Committing Sudoku.");
                System.exit(0);

            } catch (Exception e) {
                sendMessage("❌ RESTART FAILED: " + e.getMessage());
            }
        } else if (command.equals("/console")) {
            System.out.println("  [TelegramBot] Executing /console subroutine...");
            TradeExecutor.executeConsoleCheck("🖥️ Live Trading Engine Console Output");
        } else {
            sendMessage(
                    "❓ Unknown command. Available commands:\n/status - Check sync & TOS screen\n/console - Live Java terminal logs\n/restart - Reboot Engine");
        }
    }

    private void sendMessage(String text) {
        try {
            HttpUrl url = HttpUrl.parse(API_URL + "/sendMessage").newBuilder()
                    .addQueryParameter("chat_id", CHAT_ID)
                    .addQueryParameter("text", text)
                    .build();
            Request request = new Request.Builder().url(url).build();
            client.newCall(request).execute().close();
        } catch (Exception e) {
            System.out.println("  [TelegramBot] Failed to send text: " + e.getMessage());
        }
    }
}
