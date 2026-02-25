package com.schwab.api;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.FileInputStream;
import java.io.OutputStream;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

public class TradeExecutor {

    private static final int[] COORDS_BUY = { 1382, 147 };
    private static final int[] COORDS_SELL = { 1480, 148 };
    private static final int[] COORDS_QTY = { 1451, 233 };
    private static final int[] COORDS_LIMIT = { 1631, 340 };
    private static final int[] COORDS_STOP = { 1631, 361 };

    private static Robot robot;

    public interface ExtUser32 extends User32 {
        ExtUser32 INSTANCE = Native.load("user32", ExtUser32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetForegroundWindow(HWND hWnd);

        boolean ShowWindow(HWND hWnd, int nCmdShow);

        boolean IsIconic(HWND hWnd);

        boolean BringWindowToTop(HWND hWnd);

        void SwitchToThisWindow(HWND hWnd, boolean fAltTab);

        int GetWindowThreadProcessId(HWND hWnd, IntByReference lpdwProcessId);

        HWND GetForegroundWindow();

        int GetCurrentThreadId();

        boolean AttachThreadInput(int idAttach, int idAttachTo, boolean fAttach);

        boolean GetWindowRect(HWND hWnd, RECT rect);
    }

    public interface ExtKernel32 extends com.sun.jna.platform.win32.Kernel32 {
        ExtKernel32 INSTANCE = Native.load("kernel32", ExtKernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        int GetCurrentThreadId();
    }

    static {
        try {
            System.setProperty("java.awt.headless", "false");
            robot = new Robot();
            robot.setAutoDelay(20);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void forceForegroundWindow(HWND hWnd) {
        int foreThread = ExtUser32.INSTANCE.GetWindowThreadProcessId(ExtUser32.INSTANCE.GetForegroundWindow(), null);
        int appThread = ExtKernel32.INSTANCE.GetCurrentThreadId();

        if (foreThread != appThread) {
            ExtUser32.INSTANCE.AttachThreadInput(foreThread, appThread, true);

            if (ExtUser32.INSTANCE.IsIconic(hWnd)) {
                ExtUser32.INSTANCE.ShowWindow(hWnd, 9); // SW_RESTORE
            }
            ExtUser32.INSTANCE.SetForegroundWindow(hWnd);

            ExtUser32.INSTANCE.AttachThreadInput(foreThread, appThread, false);
        } else {
            if (ExtUser32.INSTANCE.IsIconic(hWnd)) {
                ExtUser32.INSTANCE.ShowWindow(hWnd, 9);
            }
            ExtUser32.INSTANCE.SetForegroundWindow(hWnd);
        }

        ExtUser32.INSTANCE.SwitchToThisWindow(hWnd, true);
    }

    private static void focusThinkOrSwim() {
        System.out.println("Executing Java JNA User32 Hook to force ThinkOrSwim/Sandbox to foreground...");
        final HWND[] foundHwnd = { null };

        ExtUser32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
            @Override
            public boolean callback(HWND hWnd, Pointer arg1) {
                char[] windowText = new char[512];
                ExtUser32.INSTANCE.GetWindowText(hWnd, windowText, 512);
                String wText = Native.toString(windowText).trim().toLowerCase();

                if ((wText.startsWith("main@thinkorswim") || wText.contains("windows sandbox"))
                        && !wText.contains("tosrtdclient")) {
                    System.out.println("  [Java] >>> MATCHED TARGET WINDOW: '" + wText + "'");
                    foundHwnd[0] = hWnd;
                    return false; // Stop enumerating
                }
                return true;
            }
        }, null);

        if (foundHwnd[0] != null) {
            System.out.println("  [Java] Applying AttachThreadInput Focus Steal Bypass...");

            // Explicitly force ThinkOrSwim out of minimized hidden states and push to the
            // TOP z-index layer
            ExtUser32.INSTANCE.ShowWindow(foundHwnd[0], 9);
            ExtUser32.INSTANCE.BringWindowToTop(foundHwnd[0]);

            forceForegroundWindow(foundHwnd[0]);
            System.out.println("Foreground hook applied. Waiting 1s for animation...");
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        } else {
            System.out.println("WARNING: Could not find any window matching 'main@thinkorswim'!");
        }
    }

    private static void typeText(String val) {
        try {
            StringSelection stringSelection = new StringSelection(val);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
            Thread.sleep(100);

            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            Thread.sleep(400);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void clickType(int[] coords, String val) {
        try {
            System.out.println("   -> Clicking at [" + coords[0] + ", " + coords[1] + "] to inject: " + val);
            robot.mouseMove(coords[0], coords[1]);
            Thread.sleep(100);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(400);

            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            Thread.sleep(200);

            robot.keyPress(KeyEvent.VK_BACK_SPACE);
            robot.keyRelease(KeyEvent.VK_BACK_SPACE);
            Thread.sleep(200);

            typeText(val);

            // Critical pause before jumping to the next box!
            Thread.sleep(500);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void justClick(int[] coords) {
        robot.mouseMove(coords[0], coords[1]);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void runTestMode() throws InterruptedException {
        System.out.println("--- TRIGGERING NATIVE JAVA JNA COORDINATE TEST MODE ---");
        focusThinkOrSwim();

        System.out.println("Move mouse to QTY [Waiting 2s]...");
        Thread.sleep(2000);
        robot.mouseMove(COORDS_QTY[0], COORDS_QTY[1]);

        System.out.println("Move mouse to Limit [Waiting 2s]...");
        Thread.sleep(2000);
        robot.mouseMove(COORDS_LIMIT[0], COORDS_LIMIT[1]);

        System.out.println("Move mouse to Stop [Waiting 2s]...");
        Thread.sleep(2000);
        robot.mouseMove(COORDS_STOP[0], COORDS_STOP[1]);

        System.out.println("Move mouse to Buy [Waiting 2s]...");
        Thread.sleep(2000);
        robot.mouseMove(COORDS_BUY[0], COORDS_BUY[1]);

        System.out.println("Move mouse to Sell [Waiting 2s]...");
        Thread.sleep(2000);
        robot.mouseMove(COORDS_SELL[0], COORDS_SELL[1]);

        System.out.println("Native Test Mode Complete.");
        System.exit(0);
    }

    public static void runLoginMode(String username, String password) throws InterruptedException {
        System.out.println("--- TRIGGERING NATIVE JAVA JNA LOGIN AUTOMATION ---");
        System.out.println("Waiting for ThinkOrSwim Logon screen...");

        HWND logonHwnd = null;
        int attempts = 0;

        while (logonHwnd == null && attempts < 60) {
            final HWND[] found = { null };
            ExtUser32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
                @Override
                public boolean callback(HWND hWnd, Pointer arg1) {
                    char[] windowText = new char[512];
                    ExtUser32.INSTANCE.GetWindowText(hWnd, windowText, 512);
                    String wText = Native.toString(windowText).trim().toLowerCase();
                    if (wText.contains("logon to thinkorswim")) {
                        found[0] = hWnd;
                        return false;
                    }
                    return true;
                }
            }, null);

            logonHwnd = found[0];
            if (logonHwnd == null) {
                Thread.sleep(1000);
                attempts++;
            }
        }

        if (logonHwnd != null) {
            System.out.println("Logon screen detected! Applying foreground bypass...");
            forceForegroundWindow(logonHwnd);

            System.out.println("Waiting 5s for the ThinkOrSwim 'Installing updates' splash to clear...");
            Thread.sleep(5000);

            System.out.println("Grabbing dynamic window coordinates for the 'Proceed to login' button...");
            RECT rect = new RECT();
            ExtUser32.INSTANCE.GetWindowRect(logonHwnd, rect);
            int centerX = rect.left + ((rect.right - rect.left) / 2);
            int centerY = rect.top + (int) ((rect.bottom - rect.top) * 0.62);

            System.out.println("Clicking 'Proceed to Login >' at X:" + centerX + " Y:" + centerY);
            robot.mouseMove(centerX, centerY);
            Thread.sleep(200);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(500);

            System.out.println("Sending backup ENTER to ensure splash is cleared...");
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

            System.out.println("Waiting 3s for the 'Welcome' (Login ID) screen to slide in...");
            Thread.sleep(3000);

            forceForegroundWindow(logonHwnd);
            Thread.sleep(500);

            System.out.println("Injecting Username...");
            int inputY = rect.top + (int) ((rect.bottom - rect.top) * 0.28);
            robot.mouseMove(centerX, inputY);
            Thread.sleep(200);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(500);

            for (int i = 0; i < 30; i++) {
                robot.keyPress(KeyEvent.VK_BACK_SPACE);
                robot.keyRelease(KeyEvent.VK_BACK_SPACE);
                Thread.sleep(10);
            }
            Thread.sleep(200);

            typeText(username);
            Thread.sleep(500);

            System.out.println("Clicking 'Continue' to advance to Password screen...");
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(3000);

            System.out.println("Injecting Password...");
            robot.mouseMove(centerX, inputY);
            Thread.sleep(200);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(500);

            typeText(password);
            Thread.sleep(500);

            System.out.println("Pressing Enter! Please approve on your HP (2FA).");
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
        } else {
            System.out.println("Logon screen not found.");
        }
        System.exit(0);
    }

    public static void execute(String action, String strQty, String strLimit, String strStop, String captionData) {
        System.out.println(String.format("Delegating Trade to Native Java JNA Hook: %s %s LMT: %s STP: %s", action,
                strQty, strLimit, strStop));
        focusThinkOrSwim();

        clickType(COORDS_QTY, strQty);
        clickType(COORDS_LIMIT, strLimit);
        clickType(COORDS_STOP, strStop);

        System.out.println("   -> Committing Trade!");
        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }

        if ("BUY".equalsIgnoreCase(action)) {
            justClick(COORDS_BUY);
        } else {
            justClick(COORDS_SELL);
        }

        System.out.println("   -> Taking screenshot of executed trade...");
        try {
            Thread.sleep(1000); // Wait 1 second for TOS to register the click visually
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage capture = robot.createScreenCapture(screenRect);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File imageFile = new File("trade_" + action + "_" + timestamp + ".png");
            ImageIO.write(capture, "png", imageFile);
            System.out.println("   -> Screenshot saved: " + imageFile.getAbsolutePath());

            System.out.println("   -> Sending screenshot to Telegram...");
            sendTelegramPhoto(imageFile, captionData);
        } catch (Exception e) {
            System.out.println("Failed to capture screenshot: " + e.getMessage());
        }

        System.out.println("Native Trade Execution completed.");
        System.out.println("   -> FATAL: Trade Executed. Auto-terminating Java Trading Engine for safety.");
        System.exit(0);
    }

    public static void executeStatusCheck(String statusPayload) {
        System.out.println("   -> [Status Command] Forcing ThinkOrSwim to foreground for screenshot...");
        focusThinkOrSwim();

        try {
            Thread.sleep(1000); // Wait 1 second for TOS to render
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage capture = robot.createScreenCapture(screenRect);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File imageFile = new File("status_" + timestamp + ".png");
            ImageIO.write(capture, "png", imageFile);
            System.out.println("   -> Status Screenshot saved: " + imageFile.getAbsolutePath());

            System.out.println("   -> Sending status screenshot to Telegram...");
            sendTelegramPhoto(imageFile, statusPayload);

            // Delete the status screenshot after sending so we don't clutter the drive
            if (imageFile.exists()) {
                imageFile.delete();
            }
        } catch (Exception e) {
            System.out.println("Failed to capture status screenshot: " + e.getMessage());
        }
    }

    public static void executeConsoleCheck(String statusPayload) {
        System.out.println("   -> [Console Command] Forcing Trading Console to foreground for screenshot...");
        HWND consoleHwnd = focusConsole();

        try {
            Thread.sleep(1000); // Wait 1 second for Console to render

            Rectangle captureRect;
            if (consoleHwnd != null) {
                RECT rect = new RECT();
                ExtUser32.INSTANCE.GetWindowRect(consoleHwnd, rect);
                // Windows RECT uses left, top, right, bottom. Rectangle needs x, y, width,
                // height.
                captureRect = new Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
            } else {
                captureRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            }

            BufferedImage capture = robot.createScreenCapture(captureRect);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File imageFile = new File("console_" + timestamp + ".png");
            ImageIO.write(capture, "png", imageFile);
            System.out.println("   -> Console Screenshot saved: " + imageFile.getAbsolutePath());

            System.out.println("   -> Sending console screenshot to Telegram...");
            sendTelegramPhoto(imageFile, statusPayload);

            if (imageFile.exists()) {
                imageFile.delete();
            }
        } catch (Exception e) {
            System.out.println("Failed to capture console screenshot: " + e.getMessage());
        }
    }

    private static HWND focusConsole() {
        System.out.println("Executing Java JNA User32 Hook to force Trading Console to foreground...");
        final HWND[] foundHwnd = { null };

        ExtUser32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
            @Override
            public boolean callback(HWND hWnd, Pointer arg1) {
                char[] windowText = new char[512];
                ExtUser32.INSTANCE.GetWindowText(hWnd, windowText, 512);
                String wText = Native.toString(windowText).trim().toLowerCase();

                if (wText.contains("trading engine console")) {
                    System.out.println("  [Java] >>> MATCHED TARGET WINDOW: '" + wText + "'");
                    foundHwnd[0] = hWnd;
                    return false; // Stop enumerating
                }
                return true;
            }
        }, null);

        if (foundHwnd[0] != null) {
            // CRITICAL FIX: PowerShell windows minimize aggressively.
            // We MUST run SW_RESTORE (9) explicitly before stealing focus.
            ExtUser32.INSTANCE.ShowWindow(foundHwnd[0], 9);
            ExtUser32.INSTANCE.BringWindowToTop(foundHwnd[0]);

            forceForegroundWindow(foundHwnd[0]);
            System.out.println("Foreground hook applied. Waiting 1s for animation...");
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        } else {
            System.out.println("WARNING: Could not find any window matching 'Trading Engine Console'!");
        }

        return foundHwnd[0];
    }

    private static void sendTelegramPhoto(File file, String captionData) {
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        String chatId = System.getenv("TELEGRAM_CHAT_ID");

        if (token == null || chatId == null || token.isEmpty() || chatId.isEmpty()) {
            System.out
                    .println("   -> Skipping Telegram Photo: TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not set in .env.");
            return;
        }
        String boundary = "===" + System.currentTimeMillis() + "===";

        try {
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendPhoto");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream outputStream = connection.getOutputStream();

            // Write Chat ID
            outputStream.write(("--" + boundary + "\r\n").getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").getBytes());
            outputStream.write((chatId + "\r\n").getBytes());

            // Write Caption
            outputStream.write(("--" + boundary + "\r\n").getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").getBytes());
            outputStream.write((captionData + "\r\n").getBytes());

            // Write Photo File
            outputStream.write(("--" + boundary + "\r\n").getBytes());
            outputStream
                    .write(("Content-Disposition: form-data; name=\"photo\"; filename=\"" + file.getName() + "\"\r\n")
                            .getBytes());
            outputStream.write(("Content-Type: image/png\r\n\r\n").getBytes());

            FileInputStream inputStream = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.write(("\r\n--" + boundary + "--\r\n").getBytes());
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("   -> Telegram Image successfully injected!");
            } else {
                System.out.println("   -> Telegram API rejected connection: HTTP " + responseCode);
            }
        } catch (Exception e) {
            System.out.println("   -> FATAL ERROR: Telegram API failed! " + e.getMessage());
        }
    }
}
