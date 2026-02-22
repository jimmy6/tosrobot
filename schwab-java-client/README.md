# Schwab Streaming API Client (Java)

This project connects to the **Charles Schwab Streamer API** via WebSocket.

## Features
- Fetches `StreamerInfo` from Schwab API (`/userPreference`).
- Connects to the WebSocket.
- Authenticates (Login).
- Subscribes to Level 1 Data (Quotes) for SPY, AMD, /ES.
- Prints updates to the console.

## Usage

1. **Get an Access Token**
   You need a valid Bearer Token from Schwab's OAuth flow.
   If you have a refresh token, use a script to exchange it for an access token.

2. **Run**
   ```bat
   run_schwab_java.bat
   ```
   Paste your token when prompted.

## Code Structure
- `App.java`: Main entry point. Fetches streamer info and orchestrates connection.
- `Streamer.java`: Handles WebSocket connection, Login, and Subscription logic using `Java-WebSocket`.

## Dependencies
- `okhttp3` (REST)
- `jackson` (JSON)
- `Java-WebSocket` (Streaming)
