package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpCallHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs independent MCP calls on a shared, bounded executor.
 *
 * <p>Submission and collection are intentionally separate: a rejected call or an
 * exception from one call is represented in its own result and never cancels the
 * other calls.</p>
 */
public final class McpAnalysisExecutor {
    private final ExecutorService executor;
    private final Duration stageTimeout;
    private final Duration sessionTimeout;

    public McpAnalysisExecutor(ExecutorService executor) {
        this(executor, Duration.ofSeconds(30), Duration.ofMinutes(2));
    }

    public McpAnalysisExecutor(ExecutorService executor, Duration stageTimeout, Duration sessionTimeout) {
        this.executor = executor;
        this.stageTimeout = requirePositive(stageTimeout, "stageTimeout");
        this.sessionTimeout = requirePositive(sessionTimeout, "sessionTimeout");
    }

    public McpAnalysisExecutionResult execute(List<McpAnalysisTask> tasks) {
        Map<McpAnalysisStage, Submission> futures = new java.util.EnumMap<>(McpAnalysisStage.class);
        List<McpAnalysisStageResult> results = new ArrayList<>();
        long sessionDeadline = deadline(System.nanoTime(), sessionTimeout);

        for (McpAnalysisTask task : tasks) {
            try {
                long submittedAt = System.nanoTime();
                Submission submission = new Submission(deadline(submittedAt, stageTimeout));
                Future<?> future = executor.submit(() -> {
                    Object value = task.call().call();
                    // Capture the handle at the producer boundary.  A task may
                    // ignore interruption and return after its Future was
                    // cancelled; collecting it later would otherwise leak the
                    // protocol/session resource.
                    submission.capture(value);
                    return value;
                });
                submission.setFuture(future);
                futures.put(task.stage(), submission);
            } catch (RejectedExecutionException exception) {
                results.add(McpAnalysisStageResult.overloaded(task.stage(), exception));
            }
        }

        try {
            tasks.stream()
                    .sorted(Comparator.comparing(McpAnalysisTask::stage))
                    .filter(task -> futures.containsKey(task.stage()))
                    .forEach(task -> results.add(collect(task, futures.get(task.stage()), sessionDeadline)));
        } finally {
            // A timeout or caller interruption must never leave queued/running MCP calls behind.
            cancelUnfinished(futures);
        }

        results.sort(Comparator.comparing(McpAnalysisStageResult::stage));
        return new McpAnalysisExecutionResult(results);
    }

    private McpAnalysisStageResult collect(McpAnalysisTask task, Submission submission, long sessionDeadline) {
        Future<?> future = submission.future();
        try {
            // Do not turn a result that completed while another stage was being
            // collected into TIMEOUT.  This is also important at the global deadline.
            if (future.isDone()) {
                return resolve(task, submission, future.get(), sessionDeadline);
            }
            long remaining = Math.min(submission.deadline() - System.nanoTime(),
                    sessionDeadline - System.nanoTime());
            if (remaining <= 0) {
                return timeout(task.stage(), submission);
            }
            return resolve(task, submission, future.get(remaining, TimeUnit.NANOSECONDS), sessionDeadline);
        } catch (TimeoutException exception) {
            future.cancel(true);
            cancelHandle(submission);
            return McpAnalysisStageResult.timeout(task.stage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return McpAnalysisStageResult.failed(task.stage(), exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return McpAnalysisStageResult.failed(task.stage(), cause);
        } catch (CancellationException exception) {
            cancelHandle(submission);
            return McpAnalysisStageResult.timeout(task.stage(), exception);
        }
    }

    private McpAnalysisStageResult resolve(McpAnalysisTask task, Submission submission,
            Object value, long sessionDeadline) throws InterruptedException, ExecutionException, TimeoutException {
        if (submission.isCancelRequested()) {
            throw new CancellationException("MCP result arrived after cancellation");
        }
        if (!(value instanceof McpCallHandle)) {
            return McpAnalysisStageResult.success(task.stage(), (tools.jackson.databind.JsonNode) value);
        }
        // Normally capture() published this before the Future became done.  The
        // fallback also makes this safe for unusual Future implementations.
        McpCallHandle handle = submission.handle();
        if (handle == null) {
            if (!submission.publishHandle((McpCallHandle) value)) {
                throw new CancellationException("MCP handle arrived after cancellation");
            }
            handle = submission.handle();
        }
        if (submission.isCancelRequested() || handle == null) {
            throw new CancellationException("MCP handle arrived after cancellation");
        }
        long remaining = Math.min(submission.deadline() - System.nanoTime(),
                sessionDeadline - System.nanoTime());
        if (remaining <= 0) {
            cancelHandle(submission);
            throw new TimeoutException("MCP analysis session timed out");
        }
        try {
            var payload = handle.future().isDone()
                    ? handle.future().get()
                    : handle.future().get(remaining, TimeUnit.NANOSECONDS);
            handle.close();
            return McpAnalysisStageResult.success(task.stage(), payload);
        } catch (TimeoutException | CancellationException exception) {
            cancelHandle(submission);
            throw exception;
        }
    }

    private static McpAnalysisStageResult timeout(McpAnalysisStage stage, Submission submission) {
        // Future.cancel is only a thread interruption. The handle is the
        // protocol-level cancellation that invalidates a potentially unsafe
        // STDIO session and prevents late responses from being reused.
        submission.cancel();
        return McpAnalysisStageResult.timeout(stage, new TimeoutException("MCP analysis stage timed out"));
    }

    private static void cancelHandle(Submission submission) {
        submission.cancel();
    }

    private static void cancelUnfinished(Map<McpAnalysisStage, Submission> submissions) {
        submissions.values().stream()
                .forEach(submission -> {
                    submission.cancel();
                });
    }

    private static long deadline(long start, Duration timeout) {
        long nanos = timeout.toNanos();
        long deadline = start + nanos;
        return deadline < start ? Long.MAX_VALUE : deadline;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static final class Submission {
        private final long deadline;
        private final AtomicBoolean cancelRequested = new AtomicBoolean();
        private final AtomicReference<McpCallHandle> handle = new AtomicReference<>();
        private volatile Future<?> future;

        private Submission(long deadline) {
            this.deadline = deadline;
        }

        private void setFuture(Future<?> future) { this.future = future; }
        private void capture(Object value) {
            if (value instanceof McpCallHandle callHandle) {
                publishHandle(callHandle);
            }
        }

        private boolean publishHandle(McpCallHandle callHandle) {
            if (cancelRequested.get() || !handle.compareAndSet(null, callHandle)) {
                callHandle.cancel();
                return false;
            }
            // Close the publication race: cancellation may have happened
            // between the first check and compareAndSet.
            if (cancelRequested.get()) {
                if (handle.compareAndSet(callHandle, null)) {
                    callHandle.cancel();
                } else {
                    callHandle.cancel();
                }
                return false;
            }
            return true;
        }

        private void cancel() {
            cancelRequested.set(true); // publish cancellation before interrupting
            Future<?> submitted = future;
            if (submitted != null) {
                submitted.cancel(true);
            }
            McpCallHandle callHandle = handle.getAndSet(null);
            if (callHandle != null) {
                callHandle.cancel();
            }
        }

        private boolean isCancelRequested() { return cancelRequested.get(); }
        private McpCallHandle handle() { return handle.get(); }
        private Future<?> future() { return future; }
        private long deadline() { return deadline; }
    }
}
