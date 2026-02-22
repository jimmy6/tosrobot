' login_tos.vbs
' Asynchronous Logon Automation for ThinkOrSwim inside Windows Sandbox
Set oShell = WScript.CreateObject("WScript.Shell")

Dim args
Set args = WScript.Arguments
If args.Count < 2 Then
    WScript.Echo "Usage: cscript login_tos.vbs <username> <password>"
    WScript.Quit
End If

Dim username
Dim password
username = args(0)
password = args(1)

WScript.Echo "Waiting for ThinkOrSwim Logon Screen..."

' Loop until the logon screen appears
Dim success
success = False
Dim attempts
attempts = 0

Do While Not success And attempts < 60
    ' Look for the updater or logon screen
    success = oShell.AppActivate("Logon to thinkorswim") 
    If Not success Then
        success = oShell.AppActivate("thinkorswim updater")
    End If
    
    If Not success Then
        WScript.Sleep(1000)
        attempts = attempts + 1
    End If
Loop

If success Then
    WScript.Echo "Logon Screen Detected. Waiting for UI rendering..."
    WScript.Sleep(2000) ' Wait for the UI buttons to fully draw
    
    ' Bring it to the absolute front
    oShell.AppActivate("Logon to thinkorswim")
    WScript.Sleep(500)
    
    ' The Sandbox completely breaks the "Remember Me" hash.
    ' We must Tab through the interface to hit the login fields.
    
    ' Because of the "Proceed to Login >" screen on fresh installs/sandbox:
    ' 1. Send Enter (to click Proceed)
    oShell.SendKeys "{ENTER}"
    WScript.Sleep(2500)
    oShell.AppActivate("Logon to thinkorswim")
    WScript.Sleep(500)
    
    ' 2. Navigate to Login ID field and Type Username
    oShell.SendKeys "{TAB}"
    WScript.Sleep(500)
    
    ' Clear any cached "Remember Me" text first just in case
    oShell.SendKeys "{BACKSPACE 25}"
    WScript.Sleep(300)
    oShell.SendKeys username
    WScript.Sleep(500)
    
    ' 3. Tab to Password field
    oShell.SendKeys "{TAB}"
    WScript.Sleep(500)
    
    ' 4. Type Password and Submit
    WScript.Echo "Injecting Password..."
    oShell.SendKeys password
    WScript.Sleep(500)
    oShell.SendKeys "{ENTER}"
    
    WScript.Echo "Login Sequence Complete. Awaiting 2FA on mobile device."
Else
    WScript.Echo "Timeout waiting for ThinkOrSwim logon window."
End If
