package com.oAT.agent.sandbox.modules;

import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.core.SandboxRuntime;
import com.oAT.agent.sandbox.modules.feign.FeignSandboxModule;
import com.oAT.agent.sandbox.modules.coverage.CoverageSandboxModule;
import com.oAT.agent.sandbox.modules.http.HttpServletSandboxModule;
import com.oAT.agent.sandbox.modules.http.HttpClientV3SandboxModule;
import com.oAT.agent.sandbox.modules.http.HttpClientV4SandboxModule;
import com.oAT.agent.sandbox.modules.jdbc.ClickHouseJdbcSandboxModule;
import com.oAT.agent.sandbox.modules.jdbc.JdbcSandboxModule;
import com.oAT.agent.sandbox.modules.log.SystemLogSandboxModule;
import com.oAT.agent.sandbox.modules.mq.MqConsumerSandboxModule;
import com.oAT.agent.sandbox.modules.mq.MqProducerSandboxModule;
import com.oAT.agent.sandbox.modules.redis.RedissonSandboxModule;
import com.oAT.agent.sandbox.modules.redis.RedisSandboxModule;
import com.oAT.agent.sandbox.modules.rpc.DubboSandboxModule;
import com.oAT.agent.sandbox.modules.rpc.SofaRpcSandboxModule;
import com.oAT.agent.sandbox.modules.service.ServiceSandboxModule;
import com.oAT.agent.sandbox.modules.thread.ThreadPoolSandboxModule;

import java.util.Properties;

public class BuiltinModuleLoader {
    private static final Log logger = LogFactory.getLog(BuiltinModuleLoader.class);

    private BuiltinModuleLoader() {
    }

    public static void load(SandboxRuntime runtime) {
        Properties properties = runtime.properties();
        if (Boolean.parseBoolean(properties.getProperty("sandbox.jdbc.enabled", "false"))) {
            runtime.moduleManager().register(new JdbcSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin jdbc module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.clickhouse-jdbc.enabled", "false"))) {
            runtime.moduleManager().register(new ClickHouseJdbcSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin clickhouse-jdbc module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.service.enabled", "false"))) {
            runtime.moduleManager().register(new ServiceSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin service module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.http-servlet.enabled", "false"))) {
            runtime.moduleManager().register(new HttpServletSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin http-servlet module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.http-client-v4.enabled", "false"))) {
            runtime.moduleManager().register(new HttpClientV4SandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin http-client-v4 module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.http-client-v3.enabled", "false"))) {
            runtime.moduleManager().register(new HttpClientV3SandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin http-client-v3 module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.feign.enabled", "false"))) {
            runtime.moduleManager().register(new FeignSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin feign module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.dubbo.enabled", "false"))) {
            runtime.moduleManager().register(new DubboSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin dubbo module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.sofa-rpc.enabled", "false"))) {
            runtime.moduleManager().register(new SofaRpcSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin sofa-rpc module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.mq-producer.enabled", "false"))) {
            runtime.moduleManager().register(new MqProducerSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin mq-producer module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.mq-consumer.enabled", "false"))) {
            runtime.moduleManager().register(new MqConsumerSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin mq-consumer module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.redis.enabled", "false"))) {
            runtime.moduleManager().register(new RedisSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin redis module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.redisson.enabled", "false"))) {
            runtime.moduleManager().register(new RedissonSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin redisson module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.coverage.enabled", "false"))) {
            runtime.moduleManager().register(new CoverageSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin coverage module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.system-log.enabled", "false"))) {
            runtime.moduleManager().register(new SystemLogSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin system-log module disabled");
        }
        if (Boolean.parseBoolean(properties.getProperty("sandbox.thread-pool.enabled", "false"))) {
            runtime.moduleManager().register(new ThreadPoolSandboxModule(), runtime.moduleContext());
        } else {
            logger.info("[Sandbox] builtin thread-pool module disabled");
        }
        runtime.retransformMatchedLoadedClasses();
    }
}
