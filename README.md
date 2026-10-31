# AMA 企业智能语音会议助手

AMA 是面向企业会议场景的智能语音助手，提供会议管理、音视频或文本材料导入、转写编辑、纪要生成与文档发布等能力。

项目由 Vue 3 前端和 Spring Boot 后端组成，后端按 API、文件服务和处理 Worker 拆分。业务数据使用 MySQL，文件可以存储在本地文件系统或 S3 兼容对象存储中；用户登录通过服务端 CAS 集成完成。

## 目录

- `web/`：Vue 3 + Vite 前端。
- `backend/api/`：业务 API 和认证入口。
- `backend/file-service/`：文件上传、读取和存储服务。
- `backend/worker/`：异步处理任务。
- `contracts/openapi/`：OpenAPI 接口契约。
- `tools/`：工具链安装、开发启动和检查脚本。
- `deploy/`：部署环境清单示例。

## 本地部署

运行环境为 Linux/WSL x86_64。完整本地环境需要 Docker；工具链脚本会准备项目使用的 Java、Node.js、npm 和 Maven 版本。

```sh
python3 tools/bootstrap.py
. .tools/env.sh
npm ci --ignore-scripts
npm --prefix web ci --ignore-scripts
npm --prefix web exec playwright install chromium
./mvnw -B -ntp package
```

启动本地 API、前端、Worker、文件服务和开发依赖：

```sh
python3 tools/dev/local_app.py start \
  --web-port 5174 \
  --api-port 8182 \
  --management-port 8183
```

启动后访问 <http://127.0.0.1:5174/meetings>。需要 CAS 登录时，为启动器提供 `AMA_CAS_ORIGIN` 及对应的 CAS 服务地址。

停止本地环境并保留本地数据：

```sh
python3 tools/dev/local_app.py stop
```

## 构建与部署

构建后端和前端产物：

```sh
./mvnw -B -ntp package
npm --prefix web run build
```

API 可执行 JAR 位于 `backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar`，启动示例：

```sh
java -jar backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar
```

前端静态文件位于 `web/dist/`。部署到 Nginx 或其他静态文件服务器时，需要将前端路由（例如 `/meetings`）回退到 `web/dist/index.html`，并将 `/api` 和 `/auth` 转发到 API 服务。

部署时通过环境变量或外部配置注入数据库、认证和存储参数。常用参数包括：

| 参数 | 说明 |
| --- | --- |
| `DATABASE_URL` | MySQL JDBC 连接地址。 |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | 数据库账号和密码。 |
| `CAS_ENABLED` | 是否启用 CAS 登录，生产环境应设为 `true`。 |
| `CAS_ORIGIN` | CAS 服务地址。 |
| `CAS_SERVICE_URL` | 应用的 `/auth/cas/callback` 回调地址。 |
| `CAS_LOGOUT_SERVICE_URL` | 应用的 `/auth/cas/logout/callback` 回调地址。 |
| `STORAGE_TYPE` | `local` 或 `s3`。 |
| `STORAGE_DEFAULT_BACKEND_ID` | 默认存储后端的稳定标识。 |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_REGION` | S3 兼容存储地址、桶和区域。 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | S3 访问凭证。 |
| `PORT` / `INTERNAL_PORT` | API 和管理端口。 |

生产部署应使用密钥管理系统或受控环境变量提供密码、访问密钥和 CAS 配置，不要将真实凭证写入仓库。

## 基本验证

```sh
python3 tools/checks/run.py all
npm --prefix web run typecheck
npm --prefix web run build
npm --prefix web run check:api
```
