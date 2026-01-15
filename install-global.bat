@echo off
REM Install modinstall globally by adding to PATH
setx PATH "%PATH%;C:\Dev\modinstall\bin" /M

REM Create bin folder with the jar
if not exist "C:\Dev\modinstall\bin" mkdir "C:\Dev\modinstall\bin"

REM Copy the jar
copy /Y "C:\Dev\modinstall\build\libs\modinstall.jar" "C:\Dev\modinstall\bin\modinstall.jar"

REM Create the batch launcher
echo @echo off > "C:\Dev\modinstall\bin\modinstall.bat"
echo java -jar "%%~dp0modinstall.jar" %%* >> "C:\Dev\modinstall\bin\modinstall.bat"

echo.
echo ModInstall installed! Restart your terminal and use:
echo   modinstall install jei
echo.
