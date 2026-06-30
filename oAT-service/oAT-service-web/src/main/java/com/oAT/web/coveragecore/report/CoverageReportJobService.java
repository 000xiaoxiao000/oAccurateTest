package com.oAT.web.coveragecore.report;

import com.oAT.web.common.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

@Service
public class CoverageReportJobService implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(CoverageReportJobService.class);

    private final Set<String> runningReportGenerationKeys = ConcurrentHashMap.newKeySet();

    private ExecutorService jobExecutors;
    private java.util.concurrent.ScheduledExecutorService jobCleanupExecutor;
    private List<Job<String>> jobs;

    @Override
    public void afterPropertiesSet() {
        jobExecutors = Executors.newFixedThreadPool(5);
        jobCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        jobs = Collections.synchronizedList(new LinkedList<>());
    }

    public String startReportGeneration(String taskKey,
                                        String jobName,
                                        String startMessage,
                                        Function<Job<String>, String> worker,
                                        Function<Exception, String> errorMessageMapper) {
        if (!runningReportGenerationKeys.add(taskKey)) {
            throw new RuntimeException("该应用版本的覆盖率报告正在生成中，请等待当前任务完成后再试。");
        }

        Job<String> job = new Job<>(jobName);
        jobs.add(job);
        jobExecutors.execute(() -> {
            try {
                job.state = Job.JobState.active;
                job.getProgress().next("初始化生成任务", 5);
                job.getLogger().info(startMessage);

                String reportId = worker.apply(job);

                job.setData(reportId);
                job.state = Job.JobState.finish;
                job.getProgress().finish("报告生成完成");
                job.getLogger().info("报告生成成功: " + reportId);
                scheduleJobCleanup(job);
            } catch (Exception e) {
                logger.error("Generate report failed", e);
                job.state = Job.JobState.error;
                String errorMsg = errorMessageMapper == null ? e.getMessage() : errorMessageMapper.apply(e);
                job.getProgress().updateName("生成失败: " + errorMsg);
                job.getLogger().error("生成报告失败: " + errorMsg);
                scheduleJobCleanup(job);
            } finally {
                runningReportGenerationKeys.remove(taskKey);
            }
        });
        return job.getId();
    }

    public Job<String> getJob(String jobId) {
        return jobs.stream().filter(job -> job.getId().equals(jobId)).findFirst().orElse(null);
    }

    private void scheduleJobCleanup(Job<String> job) {
        jobCleanupExecutor.schedule(() -> {
            jobs.remove(job);
            logger.debug("Cleaned up job: {}", job.getId());
        }, 30, java.util.concurrent.TimeUnit.SECONDS);
    }
}
