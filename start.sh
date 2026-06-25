#!/bin/bash
# ============================================
# AI驱动旅游路线规划系统 - 一键启动脚本
# ============================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║     AI驱动旅游路线规划系统 - 一键启动        ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════╝${NC}"
echo ""

# ========== 检查 Java ==========
echo -e "${YELLOW}[1/4] 检查运行环境...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}[错误] 未找到 Java，请安装 JDK 17+${NC}"
    exit 1
fi
echo "   Java 版本: $(java -version 2>&1 | head -1)"

# ========== 检查 Node.js ==========
if ! command -v node &> /dev/null; then
    echo -e "${RED}[错误] 未找到 Node.js，请安装 Node.js 16+${NC}"
    exit 1
fi
echo "   Node 版本: $(node -v)"

# ========== 编译项目 ==========
echo ""
echo -e "${YELLOW}[2/4] 编译项目 (Maven Wrapper)...${NC}"
cd "$PROJECT_DIR"
export LVYOU_LLM_URL="${LVYOU_LLM_URL:-https://token-plan-cn.xiaomimimo.com/v1}"
export LVYOU_LLM_API_KEY="${LVYOU_LLM_API_KEY:-tp-cqxfulvqf88cs6t4jxh83s7xnmed7ll24ccnoocmth9l4ilx}"
export LVYOU_LLM_MODEL="${LVYOU_LLM_MODEL:-mimo-v2.5-pro}"
./mvnw clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo -e "${RED}[错误] Maven 编译失败${NC}"
    exit 1
fi
echo -e "${GREEN}   编译成功 √${NC}"

# ========== 启动 Backend 服务 (唯一启动器, 8080) ==========
echo ""
echo -e "${YELLOW}[3/4] 启动后端服务 (端口 8080, 包含Agent+业务)...${NC}"
cd "$PROJECT_DIR/backend"
java -jar target/backend-1.0.0.jar > /tmp/lvyou-backend.log 2>&1 &
BACKEND_PID=$!
echo "   后端服务 PID: $BACKEND_PID"

# 等待就绪
echo -n "   等待后端服务就绪"
for i in $(seq 1 30); do
    if curl -s http://localhost:8080 > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}   后端服务就绪 √  (http://localhost:8080)${NC}"
        break
    fi
    echo -n "."
    sleep 2
done

# ========== 启动前端 (3000) ==========
echo ""
echo -e "${YELLOW}[4/4] 启动前端服务 (端口 3000)...${NC}"
cd "$PROJECT_DIR/frontend"
if [ ! -d "node_modules" ]; then
    echo "   首次运行，安装前端依赖..."
    npm install
fi
npx vite --host > /tmp/lvyou-frontend.log 2>&1 &
FRONTEND_PID=$!
echo "   前端服务 PID: $FRONTEND_PID"

# 等待就绪
echo -n "   等待前端服务就绪"
for i in $(seq 1 15); do
    if curl -s http://localhost:3000 > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}   前端服务就绪 √  (http://localhost:3000)${NC}"
        break
    fi
    echo -n "."
    sleep 1
done

# 写入 PID 文件
echo "$BACKEND_PID" > /tmp/lvyou-backend.pid
echo "$FRONTEND_PID" > /tmp/lvyou-frontend.pid

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          所有服务启动成功!                   ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  后端服务 (Agent+业务) : http://localhost:8080 ║${NC}"
echo -e "${GREEN}║  前端页面              : http://localhost:3000 ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
echo ""
echo -e "运行 ${YELLOW}./stop.sh${NC} 停止所有服务"
echo -e "日志文件: /tmp/lvyou-backend.log /tmp/lvyou-frontend.log"
echo ""
