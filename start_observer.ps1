# Universal Sandbox Runner
# Watches for "run.ps1". Executes it. Deletes it.

$currentDir = "C:\Users\WDAGUtilityAccount\Desktop\robot"
$runFile = Join-Path $currentDir "run.ps1"
$lockFile = Join-Path $currentDir "run.lock"

Write-Host "Universal Observer Listening in: $currentDir"

while($true) {
    if (Test-Path -LiteralPath $runFile) {
        if (-not (Test-Path -LiteralPath $lockFile)) {
            # Create lock to prevent double execution
            New-Item -ItemType File -Path $lockFile -Force | Out-Null
            
            Write-Host "Executing run.ps1..."
            try {
                # Execute the script
                & $runFile
                Write-Host "Execution Complete."
            } catch {
                Write-Host "Error: $($_.Exception.Message)"
            }
            
            # Cleanup
            Remove-Item -LiteralPath $runFile -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Milliseconds 200
}