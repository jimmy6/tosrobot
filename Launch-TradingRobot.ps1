# Launch-TradingRobot.ps1
# Master Execution Script for the ThinkOrSwim Auto-Trading Robot

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$AuthScript = Join-Path $ScriptDir "auth_helper.py"
$TokenFile = Join-Path $ScriptDir "schwab_tokens.json"
$JavaDir = Join-Path $ScriptDir "schwab-java-client"
$JavaJar = Join-Path $JavaDir "target\schwab-client-1.0-SNAPSHOT.jar"

# Lock directory context for all sub-scripts (Fix for Python cwd evaluating incorrectly via Telegram watchdog)
Push-Location $ScriptDir

# Set a strict Window Title so the Telegram JNA hook can find it for /console printing
$host.ui.RawUI.WindowTitle = "Trading Engine Console"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "       THINKORSWIM ROBOT - MASTER LAUNCHER        " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""
# Load credentials from .env early so auth_helper.py can read Schwab credentials
$envFile = Join-Path $ScriptDir ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '\S' } |
        ForEach-Object {
            $pair = $_ -split '=',2
            $name = $pair[0].Trim()
            $value = $pair[1].Trim()
            Set-Item -Path "Env:$name" -Value $value
        }
}
$TOSUsername = $Env:TOS_USERNAME
$TOSPassword = $Env:TOS_PASSWORD

# Step 1: Authentication Phase
Write-Host "[1/3] Initiating Charles Schwab OAuth..." -ForegroundColor Yellow

$tokenNeedsRefresh = $true # Assume refresh is needed initially

if (Test-Path $TokenFile) {
    try {
        $TokenData = Get-Content $TokenFile | ConvertFrom-Json
        # Assuming 'access_token_expires_at' is a Unix timestamp (seconds since epoch)
        $expiresAtUnix = $TokenData.access_token_expires_at 
        
        if ($expiresAtUnix) {
            # Convert Unix timestamp to DateTime object
            $expiryDateTime = [DateTimeOffset]::FromUnixTimeSeconds($expiresAtUnix).LocalDateTime
            $currentTime = Get-Date
            $twentyFourHoursFromNow = $currentTime.AddHours(24)

            if ($expiryDateTime -gt $twentyFourHoursFromNow) {
                Write-Host "  -> Existing schwab_tokens.json found and valid for more than 24 hours. Bypassing interactive login..." -ForegroundColor Green
                $tokenNeedsRefresh = $false
            } else {
                Write-Host "  -> Existing schwab_tokens.json found, but token expires within 24 hours or is already expired. Refreshing..." -ForegroundColor Yellow
            }
        } else {
            Write-Host "  -> Existing schwab_tokens.json found, but 'access_token_expires_at' field is missing. Refreshing token..." -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  -> Error reading or parsing schwab_tokens.json. Refreshing token..." -ForegroundColor Red
    }
} else {
    Write-Host "  -> No token file found. Launching Python Auth Helper for initial login..." -ForegroundColor Yellow
}

if ($tokenNeedsRefresh) {
    python $AuthScript

    if (-not (Test-Path $TokenFile)) {
        Write-Host "`nCRITICAL ERROR: Authentication failed or was cancelled." -ForegroundColor Red
        Write-Host "No schwab_tokens.json file generated. Exiting." -ForegroundColor Red
        pause
        exit
    }
    Write-Host "  -> Token refreshed successfully." -ForegroundColor Green
}

# Step 2: Token Extraction Phase
Write-Host "`n[2/3] Extracting Access Token from payload..." -ForegroundColor Yellow
$TokenData = Get-Content $TokenFile | ConvertFrom-Json
$AccessToken = $TokenData.access_token

if ([string]::IsNullOrWhiteSpace($AccessToken)) {
    Write-Host "CRITICAL ERROR: Access Token was null or empty in the JSON payload." -ForegroundColor Red
    pause
    exit
}

# Step 3: ThinkOrSwim Logon Bypass Phase
Write-Host "`n[3/4] Activating ThinkOrSwim Automated Logon..." -ForegroundColor Yellow



Write-Host "Opening ThinkOrSwim Application..." -ForegroundColor Cyan
Start-Process -FilePath "C:\Users\WDAGUtilityAccount\Desktop\tos\thinkorswim.exe"
Start-Sleep -Seconds 5

Set-Location $JavaDir
java -jar $JavaJar --login $TOSUsername $TOSPassword

Write-Host "`n>>> Please approve the 2FA login on your phone now! <<<" -ForegroundColor Magenta
Write-Host "Waiting 15 seconds for the main Trading interface to load..." -ForegroundColor Cyan
Start-Sleep -Seconds 15

# Step 4: Engine Execution Phase
Write-Host "`n[4/4] Launching Java Trading Engine..." -ForegroundColor Yellow

Write-Host "Hunting for duplicate Java processes in memory..." -ForegroundColor Yellow
Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue 
Start-Sleep -Seconds 2

Write-Host "Starting Monitor for /MGC (Micro Gold Synchronized Minute Polling)..." -ForegroundColor Magenta

Set-Location $JavaDir
java -jar $JavaJar $AccessToken

Write-Host "`nWarning: The Java Engine has terminated." -ForegroundColor Red
pause
