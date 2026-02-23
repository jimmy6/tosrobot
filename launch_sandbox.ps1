Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    
    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    public static extern IntPtr SendMessage(IntPtr hWnd, UInt32 Msg, IntPtr wParam, IntPtr lParam);
}
"@

$WM_ENTERSIZEMOVE = 0x0231
$WM_EXITSIZEMOVE  = 0x0232
$SWP_NOACTIVATE = 0x0010
$SWP_NOZORDER = 0x0004
$FLAGS = $SWP_NOACTIVATE -bor $SWP_NOZORDER

Add-Type -AssemblyName System.Windows.Forms
$w = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width
$h = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height
if ($w -eq 0) { $w = 3440; $h = 1440 }

Write-Host "Launching Windows Sandbox in the background..."
Start-Process -FilePath "C:\workspace\thinkorswim_robot\tos_robot.wsb"

Write-Host "Waiting up to 30 seconds for Windows Sandbox Client..."
$hwnd = [IntPtr]::Zero
for ($i = 0; $i -lt 300; $i++) {
    $proc = Get-Process -Name "WindowsSandboxClient" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 }
    if ($proc) {
        $hwnd = $proc.MainWindowHandle
        break
    }
    Start-Sleep -Milliseconds 100
}

if ($hwnd -ne [IntPtr]::Zero) {
    Write-Host "Found Sandbox window. Waiting 15 seconds for internal guest OS to boot..."
    Start-Sleep -Seconds 15

    Write-Host "Simulating a human grabbing the window..."
    [Win32]::SendMessage($hwnd, $WM_ENTERSIZEMOVE, [IntPtr]::Zero, [IntPtr]::Zero)
    
    Write-Host "Snapping to ${w}x${h} without stealing focus..."
    [Win32]::SetWindowPos($hwnd, [IntPtr]::Zero, 0, 0, $w, $h, $FLAGS)
    
    # Give the RDP client a split second to register the new dimensions during the drag
    Start-Sleep -Milliseconds 200
    
    Write-Host "Simulating dropping the window (WM_EXITSIZEMOVE) to trigger RDP resolution redraw..."
    [Win32]::SendMessage($hwnd, $WM_EXITSIZEMOVE, [IntPtr]::Zero, [IntPtr]::Zero)
    
    Write-Host "Sandbox should now be ultra-wide both inside and out!"
} else {
    Write-Host "Sandbox failed to launch."
}
