# oAT-relay

中文 | [English](#english)

`oAT-relay` 是 oAccurateTest 的轻量级 HTTP 转发服务，用于在测试网络、内网隔离、代理出口或边缘节点场景中接收 Agent、覆盖率 SDK 或其他客户端请求，并按原始路径转发到 `oAT-service-web`。

---

## 模块结构

```text
oAT-relay/
├── src/main/java/com/oAT/relay/
│   ├── OAtRelayApplication.java  # Spring Boot 启动类
│   ├── RelayController.java      # 通用 HTTP 转发入口
│   └── RelayProperties.java      # oat.relay.* 配置绑定
└── src/main/resources/
    └── application.properties    # 默认端口和转发目标
```

---

## 构建

```bash
cd oAT-relay
mvn clean package
```

产出物：`target/oAT-relay-1.0.0-SNAPSHOT.jar`

---

## 启动

先启动 `oAT-service-web`，再启动 relay：

```bash
java -jar target/oAT-relay-1.0.0-SNAPSHOT.jar
```

默认监听 `18089`，并转发到 `http://127.0.0.1:8899`。

```properties
server.port=18089
oat.relay.target-base-url=http://127.0.0.1:8899
```

也可以通过启动参数覆盖：

```bash
java -jar target/oAT-relay-1.0.0-SNAPSHOT.jar \
  --server.port=18089 \
  --oat.relay.target-base-url=http://oat-service-web:8899
```

---

## 使用方式

将原本指向 `oAT-service-web` 的客户端地址改为 relay 地址即可，路径保持不变。

```text
http://127.0.0.1:8899/api/...
http://127.0.0.1:18089/api/...
```

Agent 配置示例：

```properties
server=127.0.0.1:18089
```

覆盖率 SDK 或桌面采集器中的服务端地址也可以设置为 `http://127.0.0.1:18089`。

---

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `18089` | relay 服务监听端口 |
| `oat.relay.target-base-url` | `http://127.0.0.1:8899` | 目标 `oAT-service-web` 地址 |
| `oat.relay.connect-timeout-ms` | `3000` | 连接超时 |
| `oat.relay.read-timeout-ms` | `10000` | 转发读取超时 |
| `oat.relay.max-body-size-mb` | `20` | 单次请求体大小上限 |
| `oat.relay.auth-token` | 空 | 非空时覆盖转发请求的 `Authorization` 头 |

---

## 注意事项

- relay 是 HTTP 层转发服务，不替代 `oAT-service-web` 的数据处理、存储或鉴权能力。
- 生产部署时建议通过 Nginx、网关或容器平台限制来源 IP、TLS 和访问策略。
- 大覆盖率文件上送时需同步调整 `oat.relay.max-body-size-mb`、`spring.servlet.multipart.max-request-size` 和上游网关大小限制。

---

## English

[中文](#oat-relay) | English

`oAT-relay` is a lightweight HTTP forwarding service for oAccurateTest. It receives requests from the Agent, coverage SDKs, or other clients in restricted-network or edge-node scenarios, then forwards them to `oAT-service-web` with the original path.

## Build

```bash
cd oAT-relay
mvn clean package
```

Output: `target/oAT-relay-1.0.0-SNAPSHOT.jar`

## Start

Start `oAT-service-web` first, then start the relay:

```bash
java -jar target/oAT-relay-1.0.0-SNAPSHOT.jar
```

The default port is `18089`, and the default target is `http://127.0.0.1:8899`.

```properties
server.port=18089
oat.relay.target-base-url=http://127.0.0.1:8899
```

Override them when needed:

```bash
java -jar target/oAT-relay-1.0.0-SNAPSHOT.jar \
  --server.port=18089 \
  --oat.relay.target-base-url=http://oat-service-web:8899
```

## Usage

Point clients that previously called `oAT-service-web` to the relay address. Keep the path unchanged.

```text
http://127.0.0.1:8899/api/...
http://127.0.0.1:18089/api/...
```

Agent configuration:

```properties
server=127.0.0.1:18089
```

Coverage SDKs and the desktop capture app can also use `http://127.0.0.1:18089` as the service endpoint.

## Configuration

| Property | Default | Description |
|---|---|---|
| `server.port` | `18089` | Relay listen port |
| `oat.relay.target-base-url` | `http://127.0.0.1:8899` | Target `oAT-service-web` address |
| `oat.relay.connect-timeout-ms` | `3000` | Connect timeout |
| `oat.relay.read-timeout-ms` | `10000` | Forwarding read timeout |
| `oat.relay.max-body-size-mb` | `20` | Request body limit |
| `oat.relay.auth-token` | empty | Overrides forwarded `Authorization` header when non-empty |

## Notes

- The relay forwards HTTP requests only. It does not replace `oAT-service-web` storage, processing, or authorization.
- For production deployment, restrict source IPs, TLS, and access policies through a gateway or container platform.
- For large coverage uploads, adjust `oat.relay.max-body-size-mb`, `spring.servlet.multipart.max-request-size`, and upstream gateway limits together.
