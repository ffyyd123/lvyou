@echo off
cd /d C:\lvyou
set LVYOU_LLM_URL=https://token-plan-cn.xiaomimimo.com/v1
set LVYOU_LLM_API_KEY=tp-cqxfulvqf88cs6t4jxh83s7xnmed7ll24ccnoocmth9l4ilx
set LVYOU_LLM_MODEL=mimo-v2.5-pro
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8
java -jar backend\target\backend-1.0.0.jar > backend-smoke.log 2> backend-smoke.err.log
