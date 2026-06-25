@echo off
chcp 65001 >nul
title 停止所有服务

echo.
echo ╔══════════════════════════════════════════════╗
echo ║        正在停止所有服务...                   ║
echo ╚══════════════════════════════════════════════╝
echo.

:: 停止后端服务 (8080)
echo [1/2] 停止后端服务 (8080)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
    echo    后端服务已停止 (PID: %%a)
)

:: 停止前端服务 (3000)
echo [2/2] 停止前端服务 (3000)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3000" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
    echo    前端服务已停止 (PID: %%a)
)

:: 关闭标题匹配的窗口
taskkill /f /fi "WINDOWTITLE eq Lvyou-Backend-8080*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Frontend-Dev-3000*" >nul 2>&1

echo.
echo ╔══════════════════════════════════════════════╗
echo ║        所有服务已停止!                       ║
echo ╚══════════════════════════════════════════════╝
echo.
pause
