/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.core.runtime.jobs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class JobManager {
    private static final Logger LOG = Logger.getLogger(JobManager.class.getName());

    private static final int JOB_ID_LENGTH = 8;

    private static final String LOGS_DIR = "fararoni-jobs";

    private static final int GRACEFUL_TIMEOUT_SECONDS = 5;

    public record JobInfo(
            String id,
            String command,
            boolean isAlive,
            long pid,
            Instant startTime,
            Path logFile
    ) {
        public long getUptimeSeconds() {
            return Instant.now().getEpochSecond() - startTime.getEpochSecond();
        }

        public String getSummary() {
            String status = isAlive ? "RUNNING" : "STOPPED";
            return String.format("[%s] %s - %s (PID: %d, Uptime: %ds)",
                    id, status, command, pid, getUptimeSeconds());
        }
    }

    private record JobEntry(
            Process process,
            String command,
            Path logFile,
            Instant startTime
    ) {}

    private final Map<String, JobEntry> backgroundJobs = new ConcurrentHashMap<>();

    private final Path logsDirectory;

    public JobManager() {
        this(Path.of(System.getProperty("java.io.tmpdir"), LOGS_DIR));
    }

    public JobManager(Path logsDirectory) {
        this.logsDirectory = logsDirectory;
        try {
            Files.createDirectories(logsDirectory);
        } catch (IOException e) {
            LOG.warning("[JOB-MANAGER] Could not create logs directory: " + e.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!backgroundJobs.isEmpty()) {
                LOG.warning("[JOB-MANAGER] Shutdown Hook: Terminando " +
                           backgroundJobs.size() + " procesos huerfanos...");
                killAll();
            }
        }, "JobManager-ShutdownHook"));

        LOG.info("[JOB-MANAGER] Initialized with logs at: " + logsDirectory);
    }

    public String startBackgroundJob(Path workDir, String... command) throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        String jobId = generateJobId();
        String commandStr = String.join(" ", command);
        Path logFile = logsDirectory.resolve("job_" + jobId + ".log");

        LOG.info("[JOB-MANAGER] Starting job " + jobId + ": " + commandStr);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir != null ? workDir.toFile() : new File("."));

        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        env.put("CI", "true");
        env.put("TERM", "dumb");

        Process process = pb.start();

        JobEntry entry = new JobEntry(process, commandStr, logFile, Instant.now());
        backgroundJobs.put(jobId, entry);

        LOG.info("[JOB-MANAGER] Job " + jobId + " started (PID: " + process.pid() +
                 ", Logs: " + logFile.getFileName() + ")");

        return jobId;
    }

    public String startBackgroundJob(Path workDir, Map<String, String> envVars, String... command)
            throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        String jobId = generateJobId();
        String commandStr = String.join(" ", command);
        Path logFile = logsDirectory.resolve("job_" + jobId + ".log");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir != null ? workDir.toFile() : new File("."));
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        env.put("CI", "true");
        if (envVars != null) {
            env.putAll(envVars);
        }

        Process process = pb.start();

        JobEntry entry = new JobEntry(process, commandStr, logFile, Instant.now());
        backgroundJobs.put(jobId, entry);

        LOG.info("[JOB-MANAGER] Job " + jobId + " started with custom env");

        return jobId;
    }

    public boolean killJob(String jobId) {
        JobEntry entry = backgroundJobs.remove(jobId);
        if (entry == null) {
            LOG.warning("[JOB-MANAGER] Job not found: " + jobId);
            return false;
        }

        Process process = entry.process();
        if (!process.isAlive()) {
            LOG.info("[JOB-MANAGER] Job " + jobId + " already terminated");
            return true;
        }

        terminateProcessTree(process);

        LOG.info("[JOB-MANAGER] Job " + jobId + " and descendants terminated");
        return true;
    }

    private void terminateProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();

        handle.descendants().forEach(child -> {
            LOG.fine("[JOB-MANAGER] Killing descendant PID: " + child.pid());
            child.destroyForcibly();
        });

        process.destroy();

        try {
            boolean terminated = process.waitFor(GRACEFUL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!terminated) {
                LOG.warning("[JOB-MANAGER] Process did not terminate gracefully, forcing...");
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    public boolean killJobForcibly(String jobId) {
        JobEntry entry = backgroundJobs.remove(jobId);
        if (entry == null) {
            return false;
        }

        Process process = entry.process();

        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);

        process.destroyForcibly();

        LOG.info("[JOB-MANAGER] Job " + jobId + " and descendants killed forcibly");
        return true;
    }

    public void killAll() {
        LOG.info("[JOB-MANAGER] Killing all jobs (" + backgroundJobs.size() + " active)");

        List<String> jobIds = new ArrayList<>(backgroundJobs.keySet());
        for (String jobId : jobIds) {
            killJob(jobId);
        }
    }

    public Optional<JobInfo> getJobInfo(String jobId) {
        JobEntry entry = backgroundJobs.get(jobId);
        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(new JobInfo(
                jobId,
                entry.command(),
                entry.process().isAlive(),
                entry.process().pid(),
                entry.startTime(),
                entry.logFile()
        ));
    }

    public List<JobInfo> listJobs() {
        List<JobInfo> jobs = new ArrayList<>();
        for (Map.Entry<String, JobEntry> e : backgroundJobs.entrySet()) {
            String id = e.getKey();
            JobEntry entry = e.getValue();
            jobs.add(new JobInfo(
                    id,
                    entry.command(),
                    entry.process().isAlive(),
                    entry.process().pid(),
                    entry.startTime(),
                    entry.logFile()
            ));
        }
        return jobs;
    }

    public int getActiveJobCount() {
        return (int) backgroundJobs.values().stream()
                .filter(e -> e.process().isAlive())
                .count();
    }

    public int getTotalJobCount() {
        return backgroundJobs.size();
    }

    public boolean isJobAlive(String jobId) {
        JobEntry entry = backgroundJobs.get(jobId);
        return entry != null && entry.process().isAlive();
    }

    public String getJobLog(String jobId, int lines) {
        JobEntry entry = backgroundJobs.get(jobId);
        if (entry == null || !Files.exists(entry.logFile())) {
            return "";
        }

        try {
            List<String> allLines = Files.readAllLines(entry.logFile());
            int start = Math.max(0, allLines.size() - lines);
            return String.join("\n", allLines.subList(start, allLines.size()));
        } catch (IOException e) {
            LOG.warning("[JOB-MANAGER] Could not read log for job " + jobId);
            return "";
        }
    }

    public String getFullJobLog(String jobId) {
        JobEntry entry = backgroundJobs.get(jobId);
        if (entry == null || !Files.exists(entry.logFile())) {
            return "";
        }

        try {
            return Files.readString(entry.logFile());
        } catch (IOException e) {
            return "";
        }
    }

    public Path getLogsDirectory() {
        return logsDirectory;
    }

    public int waitForJob(String jobId) throws InterruptedException {
        JobEntry entry = backgroundJobs.get(jobId);
        if (entry == null) return -1;
        return entry.process().waitFor();
    }

    public int waitForJob(String jobId, long timeout, TimeUnit unit) throws InterruptedException {
        JobEntry entry = backgroundJobs.get(jobId);
        if (entry == null) return -1;

        boolean finished = entry.process().waitFor(timeout, unit);
        return finished ? entry.process().exitValue() : -1;
    }

    public int cleanupDeadJobs() {
        int cleaned = 0;
        Iterator<Map.Entry<String, JobEntry>> it = backgroundJobs.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, JobEntry> e = it.next();
            if (!e.getValue().process().isAlive()) {
                it.remove();
                cleaned++;
                LOG.fine("[JOB-MANAGER] Cleaned up dead job: " + e.getKey());
            }
        }

        return cleaned;
    }

    private String generateJobId() {
        return UUID.randomUUID().toString().substring(0, JOB_ID_LENGTH);
    }
}
