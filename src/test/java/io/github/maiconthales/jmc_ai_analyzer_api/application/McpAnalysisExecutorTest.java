package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpCallHandle;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp.McpProtocolClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.FutureTask;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McpAnalysisExecutorTest {
    @Test
    void runsAllFiveTasksWithConfiguredConcurrencyAndKeepsFailureIsolated() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            McpAnalysisExecutor executor = new McpAnalysisExecutor(pool);
            AtomicInteger active = new AtomicInteger();
            AtomicInteger maximum = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            List<McpAnalysisTask> tasks = new ArrayList<>();

            for (McpAnalysisStage stage : McpAnalysisStage.values()) {
                tasks.add(new McpAnalysisTask(stage, () -> {
                    int now = active.incrementAndGet();
                    maximum.accumulateAndGet(now, Math::max);
                    started.countDown();
                    try {
                        if (stage == McpAnalysisStage.FREQUENT_EXCEPTIONS) {
                            throw new IllegalStateException("simulated MCP failure");
                        }
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                        return JsonNodeFactory.instance.objectNode();
                    } finally {
                        active.decrementAndGet();
                    }
                }));
            }

            var running = java.util.concurrent.CompletableFuture.supplyAsync(() -> executor.execute(tasks));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(2, maximum.get());
            release.countDown();

            McpAnalysisExecutionResult result = running.get(5, TimeUnit.SECONDS);
            assertEquals(5, result.stages().size());
            Map<McpAnalysisStage, McpAnalysisStageResult.Status> statuses = new EnumMap<>(McpAnalysisStage.class);
            result.stages().forEach(stage -> statuses.put(stage.stage(), stage.status()));
            assertEquals(4, statuses.values().stream()
                    .filter(status -> status == McpAnalysisStageResult.Status.SUCCESS).count());
            assertEquals(McpAnalysisStageResult.Status.FAILED,
                    statuses.get(McpAnalysisStage.FREQUENT_EXCEPTIONS));
            assertEquals(2, maximum.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reportsTotalFailureWhenEveryMcpStageFails() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            McpAnalysisExecutor executor = new McpAnalysisExecutor(pool,
                    Duration.ofSeconds(1), Duration.ofSeconds(2));
            List<McpAnalysisTask> tasks = java.util.Arrays.stream(McpAnalysisStage.values())
                    .map(stage -> new McpAnalysisTask(stage,
                            () -> { throw new IllegalStateException("simulated total MCP outage"); }))
                    .toList();

            McpAnalysisExecutionResult result = executor.execute(tasks);

            assertEquals(McpAnalysisStage.values().length, result.stages().size());
            assertTrue(result.stages().stream().allMatch(stage ->
                    stage.status() == McpAnalysisStageResult.Status.FAILED));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void timesOutOneSlowStageAndInterruptsItsCall() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            McpAnalysisExecutor executor = new McpAnalysisExecutor(pool,
                    Duration.ofMillis(60), Duration.ofSeconds(2));
            McpAnalysisExecutionResult result = executor.execute(List.of(
                    new McpAnalysisTask(McpAnalysisStage.AUTOMATED_RULES, () -> {
                        try {
                            Thread.sleep(Duration.ofSeconds(10));
                            return JsonNodeFactory.instance.objectNode();
                        } catch (InterruptedException exception) {
                            interrupted.countDown();
                            throw exception;
                        }
                    }),
                    new McpAnalysisTask(McpAnalysisStage.CPU_HOT_METHODS,
                            () -> JsonNodeFactory.instance.objectNode())));

            assertEquals(McpAnalysisStageResult.Status.TIMEOUT,
                    result.stages().get(0).status());
            assertEquals(McpAnalysisStageResult.Status.SUCCESS,
                    result.stages().get(1).status());
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void globalTimeoutCancelsBlockedCallsButKeepsCompletedStages() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            McpAnalysisExecutor executor = new McpAnalysisExecutor(pool,
                    Duration.ofSeconds(5), Duration.ofMillis(80));
            McpAnalysisExecutionResult result = executor.execute(List.of(
                    new McpAnalysisTask(McpAnalysisStage.AUTOMATED_RULES,
                            () -> JsonNodeFactory.instance.objectNode()),
                    new McpAnalysisTask(McpAnalysisStage.CPU_HOT_METHODS, () -> {
                        try {
                            Thread.sleep(Duration.ofSeconds(10));
                            return JsonNodeFactory.instance.objectNode();
                        } catch (InterruptedException exception) {
                            interrupted.countDown();
                            throw exception;
                        }
                    })));

            Map<McpAnalysisStage, McpAnalysisStageResult.Status> statuses = new EnumMap<>(McpAnalysisStage.class);
            result.stages().forEach(stage -> statuses.put(stage.stage(), stage.status()));
            assertEquals(McpAnalysisStageResult.Status.SUCCESS,
                    statuses.get(McpAnalysisStage.AUTOMATED_RULES));
            assertEquals(McpAnalysisStageResult.Status.TIMEOUT,
                    statuses.get(McpAnalysisStage.CPU_HOT_METHODS));
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertFalse(pool.isShutdown());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void timeoutClosesHandleWhenClientIgnoresInterrupt() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean active = new AtomicBoolean();
        AtomicBoolean resourceClosed = new AtomicBoolean();
        McpProtocolClient ignoringInterruptClient = new McpProtocolClient() {
            @Override
            public tools.jackson.databind.JsonNode callTool(String name, tools.jackson.databind.JsonNode arguments) {
                return JsonNodeFactory.instance.objectNode();
            }

            @Override
            public McpCallHandle callToolAsync(String name, tools.jackson.databind.JsonNode arguments) {
                FutureTask<tools.jackson.databind.JsonNode> future = new FutureTask<>(() -> {
                    active.set(true);
                    try {
                        release.await(); // deliberately ignores interruption
                        return JsonNodeFactory.instance.objectNode();
                    } finally {
                        active.set(false);
                    }
                });
                Thread worker = new Thread(future, "test-mcp-call");
                worker.setDaemon(true);
                worker.start();
                return new McpCallHandle(future, () -> { }, () -> {
                    resourceClosed.set(true);
                    active.set(false);
                });
            }
        };
        try {
            McpAnalysisExecutor executor = new McpAnalysisExecutor(pool,
                    Duration.ofMillis(40), Duration.ofSeconds(1));
            McpAnalysisExecutionResult result = executor.execute(List.of(
                    new McpAnalysisTask(McpAnalysisStage.AUTOMATED_RULES,
                            () -> ignoringInterruptClient.callToolAsync("tool", JsonNodeFactory.instance.objectNode()))));

            assertEquals(McpAnalysisStageResult.Status.TIMEOUT, result.stages().get(0).status());
            assertTrue(resourceClosed.get());
            assertFalse(active.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void individualTimeoutCancelsHandleReturnedAfterFutureCancellation() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cleanup = new AtomicInteger();
        AtomicReference<FutureTask<tools.jackson.databind.JsonNode>> lateFuture = new AtomicReference<>();
        try {
            McpAnalysisExecutor executor = new McpAnalysisExecutor(pool,
                    Duration.ofMillis(40), Duration.ofSeconds(1));
            var running = java.util.concurrent.CompletableFuture.supplyAsync(() -> executor.execute(List.of(new McpAnalysisTask(
                    McpAnalysisStage.AUTOMATED_RULES, () -> {
                        entered.countDown();
                        try { release.await(); } catch (InterruptedException ignored) { }
                        FutureTask<tools.jackson.databind.JsonNode> future = new FutureTask<>(
                                () -> JsonNodeFactory.instance.objectNode());
                        lateFuture.set(future);
                        return new McpCallHandle(future, () -> { }, cleanup::incrementAndGet);
                    }))));

            assertTrue(entered.await(1, TimeUnit.SECONDS));
            var result = running.get(1, TimeUnit.SECONDS);
            assertEquals(McpAnalysisStageResult.Status.TIMEOUT, result.stages().get(0).status());
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (cleanup.get() == 0 && System.nanoTime() < deadline) Thread.yield();
            assertEquals(1, cleanup.get());
            assertTrue(lateFuture.get().isCancelled());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void globalTimeoutRejectsAndCleansHandleReturnedLateExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cleanup = new AtomicInteger();
        AtomicReference<FutureTask<tools.jackson.databind.JsonNode>> lateFuture = new AtomicReference<>();
        try {
            McpAnalysisExecutor executor = new McpAnalysisExecutor(pool,
                    Duration.ofSeconds(1), Duration.ofMillis(40));
            var running = java.util.concurrent.CompletableFuture.supplyAsync(() -> executor.execute(List.of(
                    new McpAnalysisTask(McpAnalysisStage.AUTOMATED_RULES, () -> {
                        try { release.await(); } catch (InterruptedException ignored) { }
                        FutureTask<tools.jackson.databind.JsonNode> future = new FutureTask<>(
                                () -> JsonNodeFactory.instance.objectNode());
                        lateFuture.set(future);
                        return new McpCallHandle(future, () -> { }, cleanup::incrementAndGet);
                    }),
                    new McpAnalysisTask(McpAnalysisStage.CPU_HOT_METHODS, () -> {
                        try { Thread.sleep(Duration.ofSeconds(1)); } catch (InterruptedException ignored) { }
                        return JsonNodeFactory.instance.objectNode();
                     }))));

            var result = running.get(1, TimeUnit.SECONDS);
            assertEquals(McpAnalysisStageResult.Status.TIMEOUT, result.stages().get(0).status());
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (cleanup.get() == 0 && System.nanoTime() < deadline) Thread.yield();
            assertEquals(1, cleanup.get());
            assertTrue(lateFuture.get().isCancelled());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
