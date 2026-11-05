
@echo off
setlocal
title NHOM 8 - QUAN LY DAO TAO
cd /d "%~dp0"

echo ========================================
echo       WEBSITE QUAN LY DAO TAO NHOM 8
echo ========================================
echo.

if not exist "mvnw.cmd" (
    echo LOI: Khong tim thay mvnw.cmd
    pause
    exit /b 1
)

echo Dang kiem tra website...

powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8089/' -UseBasicParsing -TimeoutSec 2; exit 0 } catch { if ($_.Exception.Response) { exit 0 } else { exit 1 } }" >nul 2>&1

if not errorlevel 1 goto OPEN_WEB

echo Dang khoi dong Spring Boot...
echo Vui long doi...

start "NHOM 8 - SPRING BOOT" cmd /k "cd /d ""%~dp0"" && call mvnw.cmd spring-boot:run"

set /a COUNT=0

:WAIT_SERVER
timeout /t 2 /nobreak >nul

powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8089/' -UseBasicParsing -TimeoutSec 2; exit 0 } catch { if ($_.Exception.Response) { exit 0 } else { exit 1 } }" >nul 2>&1

if not errorlevel 1 goto OPEN_WEB

set /a COUNT+=1

if %COUNT% GEQ 30 goto FAILED

goto WAIT_SERVER

:OPEN_WEB
echo.
echo Website da san sang!
echo Dang mo trang dang nhap...

start "" "http://localhost:8089/login.html"
exit /b 0

:FAILED
echo.
echo KHONG THE KHOI DONG WEBSITE!
echo Hay kiem tra loi trong cua so Spring Boot.
pause
exit /b 1