# Gold Sniper Strategy Integration Guide

This guide explains how to integrate the Gold Sniper Strategy Engine (Java) with your TOS Automation Robot.

## 1. Building the Strategy Library

The strategy engine is packaged as a standard Java JAR file.

1.  Open a terminal in this project's root directory.
2.  Run the following command to build the JAR:
    ```powershell
    mvn clean package -DskipTests
    ```
3.  The build artifact will be located at:
    `@20260220_151510_591pnl_22dd_60wr.jar`

## 2. Integration with TOS Robot

Add the generated JAR file to your Robot's classpath.

### Initialization

You must initialize the strategy with one of the 3 validated configurations and providing a history of 1-minute candles.

**CRITICAL DATA REQUIREMENT:**
*   **Minimum:** 45,000 bars (~31 days). This is required for the ATR (Volatility) and SMA indicators to converge.
*   **Recommended:** 60-90 days.
*   **Warning:** Providing less than 31 days of history will cause **Signal Drift** (signals appearing at slightly different prices or times than the backtest).

**Available Strategy Types:**
1.  `"PUO_DI_FAN"` (Standard/Aggressive)
2.  `"SAFE"` (Multi-year Safe Mode)
3.  `"PROFIT"` (Profit Maximization Mode)

### Example Usage (Java)

```java
import com.example.api.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RobotBridge {
    public static void main(String[] args) {
        // 1. Instantiate the Strategy
        StrategyAPI strategy = new LiveStrategy();

        // 2. Prepare Historical Data (Load from your DB or CSV)
        List<Candle> history = new ArrayList<>();
        // Example: Add last 60 days of M1 data
        // history.add(new Candle(LocalDateTime.parse("2025-12-01T00:00"), 1800.0, 1801.0, 1799.0, 1800.5, 100));
        
        // 3. Initialize
        System.out.println("Initializing Strategy...");
        strategy.init("PUO_DI_FAN", history);

        // 4. Real-time Loop (Call this every minute when a new bar closes)
        while (true) {
            // Get new candle from TOS
            Candle newCandle = getLatestCandleFromTOS(); 
            
            // Get Instruction
            TradeInstruction instruction = strategy.onTick(newCandle);

            // Execute Instruction
            switch (instruction.action) {
                case BUY:
                    System.out.println("OPEN LONG! Entry: " + instruction.entryPrice + " SL: " + instruction.stopLoss + " TP: " + instruction.takeProfit);
                    // robot.placeOrder(...);
                    break;
                    
                case SELL:
                    System.out.println("OPEN SHORT! Entry: " + instruction.entryPrice + " SL: " + instruction.stopLoss + " TP: " + instruction.takeProfit);
                    break;
                    
                case CLOSE_LONG:
                case CLOSE_SHORT:
                    System.out.println("CLOSE POSITION! Reason: " + instruction.comment);
                    // robot.closeAll();
                    break;
                    
                case DO_NOTHING:
                    // System.out.println("Wait...");
                    break;
            }
            
            // Wait for next minute...
        }
    }
}
```

## 3. Data Requirements

The strategy expects **1-Minute (M1)** candlesticks.

**Candle Class:**
```java
public class Candle {
    public LocalDateTime time; // Bar Open Time
    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;
}
```

## 4. Response Format

The `onTick` method returns a `TradeInstruction` object:

```java
public class TradeInstruction {
    public enum Action { DO_NOTHING, BUY, SELL, CLOSE_LONG, CLOSE_SHORT }
    
    public Action action;
    public double entryPrice;
    public double stopLoss;
    public double takeProfit;
    public String comment;
}
```

*   **BUY/SELL:** A new trade entry signal. Includes specific SL and TP levels calculated by the engine.
*   **CLOSE_LONG/CLOSE_SHORT:** Signal to exit the current position immediately (Take Profit, Stop Loss, or Time Exit).
*   **DO_NOTHING:** No action required.
