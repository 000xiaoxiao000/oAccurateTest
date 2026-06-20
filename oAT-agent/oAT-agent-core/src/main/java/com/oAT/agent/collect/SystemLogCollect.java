package com.oAT.agent.collect;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceContext;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;

public class SystemLogCollect {
    private final static Log logger = LogFactory.getLog(SystemLogCollect.class);

    public static SystemLogCollect INSTANCE;
    private final TraceContext traceContext;

    public SystemLogCollect(TraceContext traceContext, Instrumentation instrumentation) {
        this.traceContext = traceContext;
        //代理System.out
        System.setOut(new PrintStream(System.out) {
            @Override
            public void write(int b) {
                super.write(b);
                if (getOutPut() != null) {
                    try {
                        getOutPut().write(b);
                    } catch (IOException e) {
                        logger.error("[Agent-EXCError]setOut error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                }
            }

            @Override
            public void write(byte[] b) throws IOException {
                super.write(b);
                if (getOutPut() != null) {
                    getOutPut().write(b);
                }
            }

            @Override
            public void write(byte[] buf, int off, int len) {
                super.write(buf, off, len);
                if (getOutPut() != null) {
                    try {
                        getOutPut().write(buf, off, len);
                    } catch (IOException e) {
                        logger.error("[Agent-EXCError]write error" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                }

            }
        });

        // 代理 System.err
        System.setErr(new PrintStream(System.err) {
            @Override
            public void write(int b) {
                super.write(b);
                if (getErrPut() != null) {
                    try {
                        getErrPut().write(b);
                    } catch (IOException e) {
                        logger.error("[Agent-EXCError]setErr failed. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                }
            }

            @Override
            public void write(byte[] b) throws IOException {
                super.write(b);
                if (getErrPut() != null) {
                    getErrPut().write(b);
                }
            }

            @Override
            public void write(byte[] buf, int off, int len) {
                super.write(buf, off, len);
                if (getErrPut() != null) {
                    try {
                        getErrPut().write(buf, off, len);
                    } catch (IOException e) {
                        logger.error("[Agent-EXCError]write error" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                }
            }
        });
        logger.info("[Agent-info]完成 SystemLog 采集器初始化.");
    }

    private ThreadLocal<OutputStream> writersOut = new InheritableThreadLocal();
    private ThreadLocal<OutputStream> writersErr = new InheritableThreadLocal();

    private OutputStream getOutPut() {
        return writersOut.get();
    }

    private OutputStream getErrPut() {
        return writersErr.get();
    }

    public void setOutput(OutputStream out) {
        OutputStream current = writersOut.get();
        if (current != null) {
            // 尝试关闭旧的 writer，防止资源泄漏
            try {
                current.close();
            } catch (Exception e) {
                // 记录关闭异常但不中断流程
                logger.warn("[Agent-WARN]关闭旧的 system 日志 writer 异常", e);
            }
            writersOut.remove();
        }
        writersOut.set(out);
    }

    public void removeOutput(OutputStream out) {
        OutputStream current = writersOut.get();
        if (current == out) {
            writersOut.remove();
        } else if (current != null) {
            throw new IllegalStateException("[Agent-EXCError]system 日志采集状态异常：'writer' 清除失败");
        }
    }

    public void setErrOutput(OutputStream err) {
        OutputStream current = writersErr.get();
        if (current != null) {
            // 尝试关闭旧的 writer，防止资源泄漏
            try {
                current.close();
            } catch (Exception e) {
                // 记录关闭异常但不中断流程
                logger.warn("[Agent-WARN]关闭旧的 system 日志 writer 异常", e);
            }
            writersErr.remove();
        }
        writersErr.set(err);
    }

    public void removeErrOutput(OutputStream err) {
        OutputStream current = writersErr.get();
        if (current == err) {
            writersErr.remove();
        } else if (current != null) {
            throw new IllegalStateException("[Agent-EXCError]system 日志采集状态异常：'writer' 清除失败");
        }
    }
}
