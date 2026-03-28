# JFV1-agent工程
是给目标系统提供javaagent服务；其中JFV1-agent-core模块是核心模块，负责提供javaagent的加载等功能；<br />
collect模块是协议（如mysql，redis等）数据收集模块，负责收集目标系统的运行数据，并将数据发送给JFV1-server进行分析和展示；<br />
common模块是公共模块，包含了一些工具类和公共类；<br />
jacoco模块是代码覆盖率模块，用于统计代码的测试覆盖率；<br />
trace模块是链路追踪模块，用于记录目标系统的调用链路信息；<br />
transfer模块是数据传输模块，用于将数据从JFV1-agent传输到JFV1-server；<br />
Agent启动流程：
1. 启动JFV1-agent-core模块，加载javaagent；<br />
2. 启动JFV1-collect模块，启动数据收集线程；<br />


# JFV1-service工程
负责接收JFV1-agent传输过来的数据，并进行数据分析和展示；
## JFV1-client-model模块是客户端模型模块，用于定义客户端请求的模型；
agent.model模块是客户端agent模块，用于定义客户端agent的模型；<br />
server.model模块是服务端服务模块，用于定义服务端服务的模型；<br />

## JFV1-server-web模块是服务端web模块，负责提供web界面；
common模块是公共模块，包含了一些工具类和公共类；<br />
其中compare模块是代码比对模块，对比jar或war包的字节码/JVM指令码的差异；<br />
config模块是配置模块，用于加载配置文件；<br />
control模块是控制器模块，用于处理客户端请求；<br />
domain模块是领域模块，用于处理图层业务逻辑；<br />
esDAO模块是ES数据访问模块，用于访问ES数据库；<br />
exceptions模块是异常模块，用于处理异常；<br />
service模块是服务模块，用于处理实例与实现逻辑；<br />

resources模块是资源模块，用于加载静态资源；<br />
其中static模块存放css，images，js；<br />
templates模块存放页面模板；<br />

