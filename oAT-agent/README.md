# 一、项目描述

---
# 

# 二、maven引入oAT-client-model依赖包
先将oAT-service工程的oAT-client-model子工程编译打包，再将编译后的jar包放入maven仓库中，然后在工程中引入依赖：
```xml
<dependency>
<groupId>oAT</groupId>
<artifactId>oAT-client-model</artifactId>
<version>1.0-SNAPSHOT</version>
</dependency>
```

# 三、javaagent代理/探针配置<br />
---
***jvm参数配置***<br />
|app home即目标系统|<br />
-javaagent: {app home}/oAT-agent-core-1.0-SNAPSHOT.jar=appKey=1xxx<br />
可选：<br />
递归深度限制， -DoAT.jacoco.stack.maxrecursiondepth=1 <br />
最大堆栈节点数，-DoAT.jacoco.stack.maxsize=10000 <br/>
参数名称	                                |  默认值   |   建议范围    | 说明    <br />
oAT.jacoco.stack.maxrecursiondepth	|    1     |     1-5	 |最大递归深度限制   <br />
oAT.jacoco.stack.maxsize	        |  自动计算 | 10000-100000 | 最大堆栈节点数     <br />

经验公式（估算）：<br />
普通业务：-Xss1m<br />
有深递归/极大方法：-Xss2m ~ -Xss4m<br />
极端情况（如递归>5000层）：-Xss8m 或更高<br />

HttpClient异步线程池配置：<br />
参数名称	                                     |  默认值   |   建议范围     | 说明    <br />
oAT.agent.http.max.queue.size         |  8192    |  根据并发量调整 | 最大队列大小，用于控制等待处理的HTTP请求队列的长度。<br />
oAT.agent.http.core.pool.size         |  20      |  根据并发量调整 | 连接池的核心大小，即池中保持的最小连接数。<br />
oAT.agent.http.max.pool.size          |  160     |  根据并发量调整 | 连接池的最大大小，即池中允许的最大连接数。<br />
oAT.agent.http.keep.alive.time        |  60L     |  根据并发量调整 | 连接保持活动的时间（通常以毫秒或秒为单位），即在空闲状态下保持连接打开的时长。<br />
oAT.agent.http.base.timeout.ms        |  10000   |  根据并发量调整 | 基础超时时间（毫秒），可能用于请求或连接的基本超时设置。<br />
oAT.agent.http.per.param.timeout.ms   |  50      |  根据并发量调整 | 每个参数的超时时间（毫秒），可能用于控制每个请求参数的处理超时。<br />
oAT.agent.http.read.timeout.extra.ms  |  5000    |  根据并发量调整 | 读取超时的额外时间（毫秒），可能是在基础读取超时上增加的额外时间。<br />

**agent配置**<br />
|agent home即探针安装目录|<br />
配置文件： {agent home}/conf/oAT.conf<br />
或 {agent home}/conf/oAT_appKey.conf<br />
| 字段 | 描述  | 默认值 |<br />
|:----|<br />
| server | 【必填】远程服务（server-web的ip和port）地址：示例127.0.0.1:8899，本地oAT-service-web服务的ip和port | 无 |<br />
| heartbeatTime | 心跳间隔(秒) | 20 |<br />
| sessionTimeout | 会话超时(秒) | 60 |<br />
| agentVersion   | 探针版本（保留）   | 无   |<br />

| log.level | 日志级别 | info |<br />
| log.level | 日志级别 | warn |<br />
| log.level | 日志级别 | error |<br />
| log.level | 日志级别 | debug |<br />
| log.level | 日志级别 | trace |<br />

配置方式**：**基于项目配置中的在线配置<br />
| 字段 | 描述  | 默认值 |<br />
|:----|<br />
| codeStackInclude | 代码采集包括包名。基于简易匹配 |    |<br />
| codeStackExclude | 代码采集排除包名。基于简易匹配 |    |<br />
| codeStackIncludeMethod | 代码采集包括方法 。基于简易匹配，需要先开启codeStackInclude才可使用该字段 |  默认是全部方法，方法名称为方法名  |<br />
| codeStackExcludeMethod | 代码采集排除方法。基于简易匹配 |  该字段为空默认排除的方法包含字段get, set, add, hashCode, toString, equals, Equal  |<br />
| codeStackExcludeClassloader | 堆栈采集排除的ClassLoader。基于简易匹配 |    |<br />
| urlExclude | http 采集包括。基于正则表达示 |    |<br />
| serviceInclude | 服务采集包括包名。基于简易匹配。线程池可使用此方式 |    |<br />
| serviceExclude | 服务采集排除包名。基于简易匹配。 |    |<br />
| serviceIncludeMethod | 服务采集包括方法名和入参。基于简易匹配。| 屏蔽非公共方法，屏蔽静态方法，屏蔽本地方法，字节码中 bridge 和 synthetic 方法|<br />
| serviceExcludeMethod | 服务采集排除方法名和入参。基于简易匹配。 |    |<br />

例如：<br />
#服务按包名追踪<br />
conf_service.include=com.xxxx.\*&com.yyyy.\* 或 com.\*.zzz<br />
#服务按包名排除追踪<br />
conf_service.exclude=<br />
#应用包括（方法名）
service.includeMethod=test() 或 test(\*) 或 test(java.lang.String) 或 test(\*.String)<br />
#应用排掉（（方法名）
#service.excludeMethod=<br />
#代码追踪范围，远程web未启动时使用，注：只能追踪到类<br />
conf_codeStack.include=com.xxxx.*<br />
#代码追踪范围，远程web未启动时使用，注：需要先开启conf_codeStack.include才可使用该字段，用于追踪到方法<br />
#conf_codeStack.includeMethod=<br />
#代码追踪范围排除<br />
#conf_codeStack.exclude=<br />
#conf_codeStack.excludeMethod=<br />

**简易匹配规则说明**：列表条目由and符号（&）分隔，可以使用通配符（*和?）<br />
例如：com.xxxx.*?&com.yyyy.*&com.zzzz.* <br />

#注：<br />
#1、启动 service.include 配置，以下协议可以不开启，防止冲突。<br />
#HTTP请求与响应采集
"collect.HttpServlet",
"collect.httpRequestParams", "collect.httpRequestBody", "collect.httpResponseBody",
#中间件采集开关
"collect.systemLog", "collect.feignInvoker", "collect.dubboInvoker",
"collect.dubboReceive", "collect.sofaProviderInvoker", "collect.sofaConsumerInvoker",
"collect.jdbc", "collect.clickHouseJdbc", "collect.redis",
"collect.redisson", "collect.rabbitMq", "collect.rabbitMqReceive",
"collect.rocketMq", "collect.rocketMqReceive", "collect.kafkaMq",
"collect.kafkaMqReceive", "collect.httpClientV3", "collect.httpClientV4"
<br />

#2、Tomcat 的默认 POST 请求大小限制为 2MB。<br />
```properties
server.tomcat.max-http-post-size=100MB  # 允许 POST 请求最大为 100MB
```
#Nginx 默认的请求体最大大小是 1MB，可以通过 client_max_body_size 指令进行调整。<br />
#Apache默认情况下，对请求体大小没有硬性限制，但可以通过 LimitRequestBody 指令进行设置。LimitRequestBody 1048576  # 限制为 1MB。<br />

###
约定：
配置更改-必须重启<br />
启动顺序：<br />
1、启动中间件（ES, MySql, Redis）<br />
2、启动 server 平台<br />
3、启动加载探针的目标程序<br />

oAT-service-web启动方式：<br />
后台启动：```bash nohup java -jar oAT-service-web-1.0-SNAPSHOT.war &```<br />
后台启动，指定输出日志：```bash nohup java -jar oAT-service-web-1.0-SNAPSHOT.war > output.log 2>&1 &```<br />
