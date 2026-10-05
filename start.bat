@echo off
setlocal
if "%PORT%"=="" set PORT=8080
if "%STAFF_PIN%"=="" set STAFF_PIN=change-me
if "%DATA_DIR%"=="" set DATA_DIR=.
echo Starting QuickBite on http://localhost:%PORT%
java Server.java
pause
