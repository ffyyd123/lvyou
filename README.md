# 🗺️ AI驱动旅游路线规划系统

## 项目概述

基于 **AgentScope Harness 2.0** 框架构建的 AI 旅行规划引擎，采用 **ReAct 推理模式**（思考→行动→观察→输出），自动为用户生成可执行的结构化旅行路线，并通过高德地图进行可视化展示。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    前端 (Vue 3 + Vite)                       │
│   TravelForm → TripList → MapView (高德地图可视化)           │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP /api/travel/plan
┌──────────────────────▼──────────────────────────────────────┐
│              后端业务系统 (Spring Boot 3.2)                   │
│   Controller → Service → AgentClient (纯业务编排, 无AI逻辑)  │
│   端口: 8080    |   唯一启动器: LvyouApplication              │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP /agent/plan
┌──────────────────────▼──────────────────────────────────────┐
│          AI Agent 服务 (AgentScope Harness 2.0.0-RC3)        │
│   HarnessAgent (ReAct推理) + Toolkit (3个Tool) + Memory     │
│   端口: 8081                                                 │
│                                                              │
│   ┌──────────┐  ┌──────────────┐  ┌───────────────┐        │
│   │search_poi│  │calc_distance │  │validate_route │        │
│   │  POI搜索  │  │  距离计算    │  │  路线校验     │        │
│   └──────────┘  └──────────────┘  └───────────────┘        │
│                                                              │
│   PoiDataStore (内存Map, 从 poi_data.json 加载)              │
│   TravelMemory (会话记忆, 偏好管理)                          │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────────────────┐
│              LLM 大模型 (通义千问 DashScope)                  │
│              qwen-max / qwen-plus                            │
└─────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | Vue 3 + Vite | SPA应用，高德地图JS API 2.0 |
| 后端 | Spring Boot 3.2 | REST API + 业务编排（唯一启动器） |
| AI框架 | AgentScope Harness 2.0.0-RC3 | ReAct推理 + Tool系统 + Memory |
| LLM | 通义千问 qwen-max | DashScope API |
| 数据 | 内存Map + JSON文件 | 禁止使用数据库 |
| 构建 | Maven 父子模块 | parent → backend + agent-service |

## Maven 模块结构

```
lvyou-parent (pom)
├── agent-service     AI Agent服务 (AgentScope Harness)
└── backend           业务系统 (唯一启动器, 依赖agent-service)
```

## 快速启动

### 1. 环境要求

- **JDK 17+** (无需安装 Maven，项目自带 Maven Wrapper)
- Node.js 18+
- 高德地图 JS API Key
- 阿里云 DashScope API Key

### 2. 配置 API Key

在启动前设置 DashScope API Key：

**Windows (命令提示符):**
```cmd
set DASHSCOPE_API_KEY=your-dashscope-api-key
```

**Linux/Mac:**
```bash
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

> 也可在 `agent-service/src/main/resources/application.yml` 中直接配置 `llm.api-key`

### 3. 一键启动（推荐）

**Windows:** 双击 `start.bat` 或在命令提示符中运行：
```cmd
start.bat
```

**Linux/Mac:**
```bash
chmod +x start.sh mvnw
./start.sh
```

脚本会自动：编译项目 → 启动 Agent 服务(8081) → 启动 Backend(8080) → 启动前端(3000) → 打开浏览器

### 4. 停止服务

**Windows:**
```cmd
stop.bat
```

**Linux/Mac:**
```bash
./stop.sh
```

### 5. 手动启动（分步）

```bash
# 编译项目 (使用 Maven Wrapper，无需安装 Maven)
# Windows: mvnw.cmd clean package -DskipTests
# Linux/Mac: ./mvnw clean package -DskipTests

# 启动 Agent 服务 (端口 8081)
cd agent-service
java -jar target/agent-service-1.0.0.jar

# 启动业务系统 (端口 8080，另开终端)
cd backend
java -jar target/backend-1.0.0.jar

# 启动前端 (端口 3000，另开终端)
cd frontend
npm install
npm run dev
```

### 6. 访问

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:3000 |
| 后端 API | http://localhost:8080 |
| Agent 服务 | http://localhost:8081 |

## API 接口

### POST /api/travel/plan

生成旅行路线计划。

**请求体：**
```json
{
  "from": "北京",
  "to": "山西",
  "days": 5,
  "preference": "历史文化"
}
```

**响应体：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "from": "北京",
    "to": "山西",
    "totalDays": 5,
    "preference": "历史文化",
    "days": [
      {
        "day": 1,
        "theme": "太原古城探索",
        "pois": [
          {
            "name": "晋祠",
            "lat": 37.71,
            "lng": 112.44,
            "stay_time": 120,
            "description": "中国现存最早的皇家园林"
          }
        ],
        "distance": 80.5,
        "drive_time": 120
      }
    ]
  }
}
```

## ReAct 推理流程

```
用户请求 → HarnessAgent
  ├── Thought: "需要先搜索太原的历史文化景点"
  ├── Action: 调用 search_poi("山西", "历史文化")
  ├── Observation: "找到晋祠、平遥古城、云冈石窟等..."
  ├── Thought: "需要计算晋祠到平遥古城的距离"
  ├── Action: 调用 calc_distance(37.71, 112.44, 37.20, 112.18)
  ├── Observation: "距离约65公里，驾车约97分钟"
  ├── Thought: "需要校验 Day1 路线是否合理"
  ├── Action: 调用 validate_route(97, 420, 3)
  ├── Observation: "路线合理！"
  └── Final Answer: 输出JSON路线
```

## Tool 系统

| Tool | 功能 | 输入 | 输出 |
|------|------|------|------|
| search_poi | 搜索景点POI | 城市 + 关键词 | POI列表（名称/经纬度/停留时间） |
| calc_distance | 计算距离 | 两个经纬度 | 距离(km) + 驾车时间(min) |
| validate_route | 校验路线 | 驾车时间 + 游玩时间 + 景点数 | 合理性评估 |

## 禁止事项

- ❌ 不使用数据库（MySQL/Redis/MQ/ES）
- ❌ Java 业务层不包含任何 AI 逻辑
- ❌ 不做旅游攻略文章系统
- ❌ 不做社区/评论/用户系统
- ❌ AI 输出必须是结构化 JSON，不允许自然语言路线
"# lvyou" 
