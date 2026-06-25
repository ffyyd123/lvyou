#!/bin/bash
# ============================================
# AI驱动旅游路线规划系统 - 停止所有服务
# ============================================

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║        正在停止所有服务...                   ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
echo ""

# 按 PID 文件停止
for svc in backend frontend; do
    PID_FILE="/tmp/lvyou-$svc.pid"
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID" 2>/dev/null
            echo -e "   ${GREEN}已停止 $svc 服务 (PID: $PID)${NC}"
        fi
        rm -f "$PID_FILE"
    fi
done

# 兜底：按端口杀进程
echo ""
echo "   清理残留进程..."

PID_8080=$(lsof -ti:8080 2>/dev/null)
[ -n "$PID_8080" ] && kill $PID_8080 2>/dev/null && echo "   已清理端口 8080"

PID_3000=$(lsof -ti:3000 2>/dev/null)
[ -n "$PID_3000" ] && kill $PID_3000 2>/dev/null && echo "   已清理端口 3000"

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║        所有服务已停止!                       ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
echo ""
