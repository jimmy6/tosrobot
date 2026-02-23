package com.schwab.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import com.example.api.Candle;
import com.example.api.LiveStrategy;
import com.example.api.StrategyAPI;
import com.example.api.TradeInstruction;

public class App {
    private static final String API_BASE = "https://api.schwabapi.com/marketdata/v1";
    private static String ACCESS_TOKEN = "";

    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static long lastTotalVolume = -1;
    private static long lastCandleTime = -1;
    private static long lastTokenRefreshTime = System.currentTimeMillis();
    private static StrategyAPI strategy = new LiveStrategy();

    public static long getLastCandleTime() {
        return lastCandleTime;
    }

    private static void refreshAccessToken() {
        System.out.println("  [!] Schwab Access Token expired! Auto-refreshing via Python...");
        try {
            // Define the path to auth_helper.py
            String scriptPath = "C:\\Users\\WDAGUtilityAccount\\Desktop\\robot\\auth_helper.py";
            String jsonPath = "C:\\Users\\WDAGUtilityAccount\\Desktop\\robot\\schwab_tokens.json";

            // Run the python script
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath);
            pb.directory(new File("C:\\Users\\WDAGUtilityAccount\\Desktop\\robot"));
            pb.inheritIO();
            Process p = pb.start();

            // Wait for it to finish fetching the new token
            p.waitFor();

            // Read the updated token file
            String content = new String(Files.readAllBytes(Paths.get(jsonPath)));
            JsonNode root = mapper.readTree(content);
            if (root.has("access_token")) {
                ACCESS_TOKEN = root.get("access_token").asText();
                System.out.println("  [v] Token successfully renewed!");
            } else {
                System.out.println("  [X] Failed to parse updated access_token from JSON.");
            }
        } catch (Exception e) {
            System.out.println("  [X] Auto-refresh failed: " + e.getMessage());
        }
    }

    private static void printBanner() {
        System.out.println("==========================================================================");
        System.out.println("   ###    ##     ##     ######  ##     ##  #######  #### ");
        System.out.println("  ## ##   ##     ##    ##    ## ##     ## ##     ##  ##  ");
        System.out.println(" ##   ##  ##     ##    ##       ##     ## ##     ##  ##  ");
        System.out.println("##     ## #########    ##       ######### ##     ##  ##  ");
        System.out.println("######### ##     ##    ##       ##     ## ##     ##  ##  ");
        System.out.println("##     ## ##     ##    ##    ## ##     ## ##     ##  ##  ");
        System.out.println("##     ## ##     ##     ######  ##     ##  #######  #### ");
        System.out.println("");
        System.out.println("########  ##     ##  #######  ########  #### ########    ###    ##    ## ");
        System.out.println("##     ## ##     ## ##     ## ##     ##  ##  ##         ## ##   ###   ## ");
        System.out.println("##     ## ##     ## ##     ## ##     ##  ##  ##        ##   ##  ####  ## ");
        System.out.println("########  ##     ## ##     ## ##     ##  ##  ######   ##     ## ## ## ## ");
        System.out.println("##        ##     ## ##     ## ##     ##  ##  ##       ######### ##  #### ");
        System.out.println("##        ##     ## ##     ## ##     ##  ##  ##       ##     ## ##   ### ");
        System.out.println("##         #######   #######  ########  #### ##       ##     ## ##    ## ");
        System.out.println("==========================================================================");
        System.out.println("                     VERSION 1.0 - STREAM LIVE                    ");
        System.out.println("==========================================================================\n");
    }

    public static void main(String[] args) {
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(new File("app.lock"), "rw");
            FileChannel fileChannel = randomAccessFile.getChannel();
            FileLock lock = fileChannel.tryLock();
            if (lock == null) {
                System.out.println("\n[!] CRITICAL ALERT: Another instance of the Trading Engine is already running!");
                System.out.println("[!] Terminating this duplicate instance immediately to prevent double-trades.\n");
                System.exit(1);
            }
            // Lock acquired successfully! Keep the file channel open for the life of the
            // JVM.
        } catch (Exception e) {
            System.out.println("Failed to acquire application lock. Terminating.");
            System.exit(1);
        }

        printBanner();

        if (args.length > 0 && args[0].equals("--test-coords")) {
            System.out.println("Running Coordinate Configuration Test...");
            try {
                TradeExecutor.runTestMode();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (args.length >= 3 && args[0].equals("--login")) {
            System.out.println("Running Automated Logon...");
            try {
                TradeExecutor.runLoginMode(args[1], args[2]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (args.length >= 5 && args[0].equals("--test-trade")) {
            System.out.println("Running Simulated Trade Execution Test...");
            try {
                String testCaption = args[1] + " " + args[2] + " @ X.XX (LMT: " + args[3] + " | STP: " + args[4] + ")";
                TradeExecutor.execute(args[1], args[2], args[3], args[4], testCaption);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (args.length > 0) {
            ACCESS_TOKEN = args[0];
        }

        if (ACCESS_TOKEN.isEmpty()) {
            System.out.println(
                    "Usage: java -jar <jar> <ACCESS_TOKEN> OR --test-coords OR --login <user> <pass> OR --test-trade <BUY|SELL> <qty> <lmt> <stp>");
            return;
        }

        System.out.println("--- Schwab Java Client (Polling 1-Min Candle) ---");
        System.out.println("Fetching last closed candle for /MGC every minute...");

        String symbol = "/MGC";
        String encodedSymbol = symbol.replace("/", "%2F");

        try {
            Request initReq = new Request.Builder()
                    .url(API_BASE + "/quotes?symbols=" + encodedSymbol)
                    .addHeader("Authorization", "Bearer " + ACCESS_TOKEN)
                    .build();
            try (Response resp = client.newCall(initReq).execute()) {
                if (resp.isSuccessful()) {
                    JsonNode qRoot = mapper.readTree(resp.body().string());
                    Iterator<String> fieldNames = qRoot.fieldNames();
                    if (fieldNames.hasNext()) {
                        lastTotalVolume = qRoot.get(fieldNames.next()).path("quote").path("totalVolume").asLong();
                        System.out.println("Initial Daily Volume: " + lastTotalVolume);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to initialize volume tracking: " + e.getMessage());
        }

        // Fetch Historical Data mapping
        List<Candle> history = new ArrayList<>();
        System.out.println("Fetching 35 Days of Historical 1-Min Data for Engine Warmup...");
        try {
            long nowMs = System.currentTimeMillis();
            long startMs = nowMs - (35L * 24L * 60L * 60L * 1000L); // 35 days ago

            HttpUrl histUrl = HttpUrl.parse(API_BASE + "/pricehistory").newBuilder()
                    .addQueryParameter("symbol", symbol)
                    .addQueryParameter("frequencyType", "minute")
                    .addQueryParameter("frequency", "1")
                    .addQueryParameter("endDate", String.valueOf(nowMs))
                    .addQueryParameter("startDate", String.valueOf(startMs))
                    .addQueryParameter("needExtendedHoursData", "true")
                    .build();

            Request histReq = new Request.Builder()
                    .url(histUrl)
                    .addHeader("Authorization", "Bearer " + ACCESS_TOKEN)
                    .build();

            try (Response histResp = client.newCall(histReq).execute()) {
                if (histResp.isSuccessful()) {
                    String json = histResp.body().string();
                    JsonNode root = mapper.readTree(json);
                    JsonNode candlesNode = root.path("candles");
                    if (candlesNode.isArray()) {
                        for (JsonNode cNode : candlesNode) {
                            long ts = cNode.path("datetime").asLong();
                            double o = cNode.path("open").asDouble();
                            double h = cNode.path("high").asDouble();
                            double l = cNode.path("low").asDouble();
                            double c = cNode.path("close").asDouble();
                            long v = cNode.path("volume").asLong();

                            Candle candle = new Candle(
                                    LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()),
                                    o, h, l, c, v);
                            history.add(candle);
                        }
                        System.out.println("Loaded " + history.size() + " historical candles.");
                        if (history.size() > 0) {
                            lastCandleTime = history.get(history.size() - 1).time.atZone(ZoneId.systemDefault())
                                    .toInstant().toEpochMilli();
                            System.out.println("Last historical candle time: " + history.get(history.size() - 1).time);
                        }
                    }
                } else {
                    System.out.println("Failed to fetch history: " + histResp.code());
                }
            }
        } catch (Exception e) {
            System.out.println("History Fetch Error: " + e.getMessage());
        }

        System.out.println("Initializing Strategy Engine...");
        strategy.init("PUO_DI_FAN", history);

        System.out.println("Starting Interactive Telegram Bot Listener Thread...");
        Thread telegramThread = new Thread(new TelegramBot());
        telegramThread.setDaemon(true); // Don't block JVM exit
        telegramThread.start();

        System.out.println("Strategy Initialized. Starting live loop...");

        try {
            while (true) {
                // Synchronize to the start of the next minute
                long now = System.currentTimeMillis();
                long nextMinute = (now / 60000 + 1) * 60000;
                long delay = nextMinute - now + 2000; // +2000ms buffer (2 seconds) to ensure candle is generated

                if (delay < 0)
                    delay += 60000;

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    break;
                }

                boolean retryNow;
                do {
                    retryNow = false;
                    try {
                        // /pricehistory request
                        HttpUrl url = HttpUrl.parse(API_BASE + "/pricehistory").newBuilder()
                                .addQueryParameter("symbol", symbol)
                                .addQueryParameter("periodType", "day")
                                .addQueryParameter("period", "1")
                                .addQueryParameter("frequencyType", "minute")
                                .addQueryParameter("frequency", "1")
                                .addQueryParameter("needExtendedHoursData", "true")
                                .build();

                        Request request = new Request.Builder()
                                .url(url)
                                .addHeader("Authorization", "Bearer " + ACCESS_TOKEN)
                                .build();

                        try (Response response = client.newCall(request).execute()) {
                            if (response.isSuccessful()) {
                                String json = response.body().string();
                                JsonNode root = mapper.readTree(json);

                                JsonNode candles = root.path("candles");
                                if (candles.isArray() && candles.size() > 0) {
                                    int index = candles.size() - 1;
                                    JsonNode candle = candles.get(index);

                                    long nowMinute = (System.currentTimeMillis() / 60000) * 60000;
                                    if (candle.path("datetime").asLong() >= nowMinute && index > 0) {
                                        index--;
                                        candle = candles.get(index);
                                    }

                                    long timeEpoch = candle.path("datetime").asLong();
                                    double open = candle.path("open").asDouble();
                                    double high = candle.path("high").asDouble();
                                    double low = candle.path("low").asDouble();
                                    double close = candle.path("close").asDouble();
                                    long tickVol = candle.path("volume").asLong();

                                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss");
                                    String timeStr = sdf.format(new java.util.Date(timeEpoch));

                                    // Fetch Quote for Current Total Volume to calculate contract delta
                                    long contractVol = 0;
                                    Request quoteReq = new Request.Builder()
                                            .url(API_BASE + "/quotes?symbols=" + encodedSymbol)
                                            .addHeader("Authorization", "Bearer " + ACCESS_TOKEN)
                                            .build();

                                    try (Response qResp = client.newCall(quoteReq).execute()) {
                                        if (qResp.isSuccessful()) {
                                            JsonNode qRoot = mapper.readTree(qResp.body().string());
                                            Iterator<String> qFields = qRoot.fieldNames();
                                            if (qFields.hasNext()) {
                                                long currentTotal = qRoot.get(qFields.next()).path("quote")
                                                        .path("totalVolume").asLong();
                                                if (lastTotalVolume != -1) {
                                                    contractVol = currentTotal - lastTotalVolume;
                                                }
                                                lastTotalVolume = currentTotal;
                                            }
                                        }
                                    }

                                    if (timeEpoch > lastCandleTime) {
                                        lastCandleTime = timeEpoch;
                                        Candle newCandle = new Candle(
                                                LocalDateTime.ofInstant(Instant.ofEpochMilli(timeEpoch),
                                                        ZoneId.systemDefault()),
                                                open, high, low, close, (double) tickVol);

                                        TradeInstruction instruction = strategy.onTick(newCandle);

                                        System.out.println("   > [ENGINE SIGNAL] " + instruction.action + " | E: "
                                                + instruction.entryPrice + " | SL: " + instruction.stopLoss + " | TP: "
                                                + instruction.takeProfit + "  (" + instruction.comment + ") . "
                                                + timeStr
                                                + " | "
                                                + open + " | " + high + " | " + low + " | " + close);

                                        // TODO: Trigger C# Executor
                                        if (instruction.action == TradeInstruction.Action.BUY
                                                || instruction.action == TradeInstruction.Action.SELL) {
                                            System.out.println("     --> EXECUTING TRADE SIGNAL!");
                                            executeTrade(instruction);
                                        } else if (instruction.action == TradeInstruction.Action.CLOSE_LONG
                                                || instruction.action == TradeInstruction.Action.CLOSE_SHORT) {
                                            System.out.println("     --> EXECUTING CLOSE SIGNAL!");
                                            // implement close logic
                                        }
                                    } else {
                                        System.out.println("   > Candle already processed.");
                                    }

                                } else {
                                    System.out.println("No candles returned.");
                                }
                            } else if (response.code() == 401) {
                                System.out.println("HTTP 401 Unauthorized detected! Token expired.");
                                refreshAccessToken();
                                lastTokenRefreshTime = System.currentTimeMillis();
                                System.out.println("  -> Retrying fetch immediately with new token...");
                                retryNow = true;
                            } else {
                                System.out.println("Error History: " + response.code());
                            }
                        }

                    } catch (Exception e) {
                        System.out.println("Poll Error: " + e.getMessage());
                    }
                } while (retryNow);

                // Now that the candle pull for this minute is 100% complete...
                // Execute proactive background token refresh if 25 mins have passed
                if (System.currentTimeMillis() - lastTokenRefreshTime > 25 * 60 * 1000) {
                    lastTokenRefreshTime = System.currentTimeMillis(); // Reset immediately so it doesn't trigger again
                    Thread bgRefresh = new Thread(() -> {
                        System.out.println("\n--- [BACKGROUND TOKEN REFRESH INITIATED] ---");
                        System.out.println(
                                "Token is approaching 30-minute expiry. Refreshing gracefully without blocking the main engine thread...");
                        refreshAccessToken();
                        System.out.println("Background token refresh completed cleanly.\n");
                    });
                    bgRefresh.setDaemon(true);
                    bgRefresh.start();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void executeTrade(TradeInstruction instruction) {
        try {
            String qty = "1"; // Assuming 1 for now, can be modified

            // Calculate relative offset gaps for ThinkOrSwim OCO templates
            double limitOffset = instruction.takeProfit - instruction.entryPrice;
            double stopOffset = instruction.stopLoss - instruction.entryPrice;

            // %+.1f forces either a '+' or '-' prefix into the string for the paste command
            String limit = String.format("%+.1f", limitOffset);
            String stop = String.format("%+.1f", stopOffset);

            String caption = String.format("%s 1 @ %.1f (LMT: %s | STP: %s)",
                    instruction.action, instruction.entryPrice, limit, stop);

            TradeExecutor.execute(instruction.action.toString(), qty, limit, stop, caption);
        } catch (Exception e) {
            System.out.println("Failed to execute trade: " + e.getMessage());
        }
    }
}