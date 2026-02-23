@echo off
echo Setting up Sandbox Environment...

echo Syncing Machine ID for ToS Verification...
reg add "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Cryptography" /v MachineGuid /t REG_SZ /d "5b0001a8-d1c8-4c94-bc01-07effebb1084" /f

echo Creating persistent AppData link...
if not exist "C:\Users\WDAGUtilityAccount\AppData\Local\thinkorswim" (
    mkdir "C:\Users\WDAGUtilityAccount\AppData\Local" 2>nul
    mklink /D "C:\Users\WDAGUtilityAccount\AppData\Local\thinkorswim" "C:\Users\WDAGUtilityAccount\Desktop\tos"
)

set INSTALL4J_JAVA_HOME=C:\Users\WDAGUtilityAccount\Desktop\tos\jre_1990.0.23
set PATH=%PATH%;C:\Users\WDAGUtilityAccount\Desktop\python;C:\Users\WDAGUtilityAccount\Desktop\python\Scripts;%INSTALL4J_JAVA_HOME%\bin
setx PATH "%PATH%;C:\Users\WDAGUtilityAccount\Desktop\python;C:\Users\WDAGUtilityAccount\Desktop\python\Scripts"
setx INSTALL4J_JAVA_HOME "C:\Users\WDAGUtilityAccount\Desktop\tos\jre_1990.0.23"

echo Installing necessary dependencies...
python -m pip install opencv-python numpy requests okhttp3

echo Updating ThinkOrSwim JRE preference...
echo C:\Users\WDAGUtilityAccount\Desktop\tos\jre_1990.0.23 > "C:\Users\WDAGUtilityAccount\Desktop\tos\.install4j\pref_jre.cfg"

echo Setup Complete.

echo Starting Background Observer...
start /min powershell -WindowStyle Hidden -ExecutionPolicy Bypass -File "C:\Users\WDAGUtilityAccount\Desktop\robot\start_observer.ps1"

echo Creating Trading Robot Desktop Shortcut...
set VBSCRIPT="%TEMP%\CreateShortcut.vbs"
echo Set oWS = WScript.CreateObject("WScript.Shell") > %VBSCRIPT%
echo sLinkFile = "C:\Users\WDAGUtilityAccount\Desktop\Run Trading Robot.lnk" >> %VBSCRIPT%
echo Set oLink = oWS.CreateShortcut(sLinkFile) >> %VBSCRIPT%
echo oLink.TargetPath = "powershell.exe" >> %VBSCRIPT%
echo oLink.Arguments = "-ExecutionPolicy Bypass -NoExit -File ""C:\Users\WDAGUtilityAccount\Desktop\robot\Launch-TradingRobot.ps1""" >> %VBSCRIPT%
echo oLink.WorkingDirectory = "C:\Users\WDAGUtilityAccount\Desktop\robot" >> %VBSCRIPT%
echo oLink.Description = "Automated Live Trading Engine" >> %VBSCRIPT%
echo oLink.IconLocation = "powershell.exe, 0" >> %VBSCRIPT%
echo oLink.Save >> %VBSCRIPT%
cscript /nologo %VBSCRIPT%
del %VBSCRIPT%

echo Starting Automated Live Trading Engine...
start powershell.exe -ExecutionPolicy Bypass -NoProfile -NoExit -File "C:\Users\WDAGUtilityAccount\Desktop\robot\Launch-TradingRobot.ps1"
