@echo off
chcp 65001 >nul
title LifeWise 监控守护
echo ========================================
echo   LifeWise 服务监控守护
echo   Ctrl+C 退出
echo ========================================
echo.

:check
echo [%date% %time%] 检查服务状态...

REM 检查后端 (8080)
curl -s -o nul -w "%%{http_code}" http://localhost:8080/api/users/login -X POST -H "Content-Type: application/json" -d "{\"phone\":\"13800138000\",\"password\":\"test123\"}" > tmp_status.txt 2>&1
set /p BACKEND_STATUS=<tmp_status.txt

if "%BACKEND_STATUS%"=="200" (
    echo [%date% %time%] ✅ 后端运行中
) else (
    echo [%date% %time%] ❌ 后端挂了，正在重启...
    cd /d D:\demo\AI\codex\LifeWise\backend
    start /B "" mvn spring-boot:run -q > D:\demo\AI\codex\LifeWise\backend.log 2>&1
    echo [%date% %time%] ✅ 后端已重启
)

REM 检查前端 (5173)
curl -s -o nul -w "%%{http_code}" http://localhost:5173/ > tmp_status2.txt 2>&1
set /p FRONTEND_STATUS=<tmp_status2.txt

if "%FRONTEND_STATUS%"=="200" (
    echo [%date% %time%] ✅ 前端运行中
) else (
    echo [%date% %time%] ❌ 前端挂了，正在重启...
    cd /d D:\demo\AI\codex\LifeWise\frontend
    start /B "" npx vite --host 0.0.0.0 > D:\demo\AI\codex\LifeWise\frontend\vite-out.txt 2>&1
    echo [%date% %time%] ✅ 前端已重启
)

del tmp_status.txt tmp_status2.txt 2>nul

echo.
timeout /t 15 /nobreak >nul
goto check
