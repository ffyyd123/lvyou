@echo off
chcp 65001 >nul
title ====== AI驱动旅游路线规划系统 ======

echo.
echo ==============================================
echo      AI驱动旅游路线规划系统 - 一键启动
echo ==============================================
echo.

REM ========== 检查 Java ==========
echo [1/4] 检查运行环境...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java，请安装 JDK 17+
    pause
    exit /b 1
)

for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%i
echo    Java 版本: %JAVA_VER%

REM ========== 检查 Node.js ==========
where node >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Node.js，请安装 Node.js 16+
    pause
    exit /b 1
)
for /f %%i in ('node -v') do set NODE_VER=%%i
echo    Node 版本: %NODE_VER%

REM ========== 编译项目 ==========
echo.
echo [2/4] 编译项目 (Maven Wrapper, 无需安装Maven)...
cd /d "%~dp0"
if "%LVYOU_LLM_URL%"=="" set "LVYOU_LLM_URL=https://token-plan-cn.xiaomimimo.com/v1"
if "%LVYOU_LLM_API_KEY%"=="" set "LVYOU_LLM_API_KEY=tp-cqxfulvqf88cs6t4jxh83s7xnmed7ll24ccnoocmth9l4ilx"
if "%LVYOU_LLM_MODEL%"=="" set "LVYOU_LLM_MODEL=mimo-v2.5-pro"
call mvnw.cmd clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo [错误] Maven 编译失败，请检查错误信息
    pause
    exit /b 1
)
echo    编译成功

REM ========== 启动 Backend 服务 (唯一启动器, 8080) ==========
echo.
echo [3/4] 启动后端服务 (端口 8080, 包含Agent+业务)...
start "Lvyou-Backend-8080" cmd /c "cd /d %~dp0backend && java -jar target/backend-1.0.0.jar"
echo    后端服务启动中...

REM 等待后端就绪
echo    等待后端服务就绪...
:wait_backend
timeout /t 2 /nobreak >nul
curl -s http://localhost:8080/api/health >nul 2>&1
if %errorlevel% neq 0 goto wait_backend
echo    后端服务就绪 (http://localhost:8080)

REM ========== 启动前端 (3000) ==========
echo.
echo [4/4] 启动前端服务 (端口 3000)...
cd /d "%~dp0frontend"
if not exist "node_modules" (
    echo    首次运行，安装前端依赖...
    call npm install
)
start "Frontend-Dev-3000" cmd /c "cd /d %~dp0frontend && npx vite --host"
echo    前端服务启动中...
echo    等待前端服务就绪...
:wait_frontend
timeout /t 2 /nobreak >nul
curl -s http://localhost:3000 >nul 2>&1
if %errorlevel% neq 0 goto wait_frontend

echo.
echo ==============================================
echo          所有服务启动成功
echo   后端服务 (Agent+业务): http://localhost:8080
echo   前端页面: http://localhost:3000
echo ==============================================
echo.
echo 按任意键打开前端页面...
pause >nul
start http://localhost:3000

echo.
echo 服务运行中，关闭此窗口不会停止服务。
echo 要停止所有服务，请运行 stop.bat
echo.
pause
