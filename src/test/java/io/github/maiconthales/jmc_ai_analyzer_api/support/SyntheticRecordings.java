package io.github.maiconthales.jmc_ai_analyzer_api.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import jdk.jfr.Recording;

/**
 * Gera gravações JFR sintéticas com os eventos que faltam nos exemplos reais: exceções lançadas,
 * leitura e escrita de arquivo e contenção de monitor.
 */
public final class SyntheticRecordings {

	private static final Object LOCK = new Object();

	private SyntheticRecordings() {
	}

	public static Path withExceptionsIoAndLocks(Path dir) {
		Path out = dir.resolve("synthetic.jfr");
		try (Recording recording = new Recording()) {
			recording.enable("jdk.JavaExceptionThrow").withStackTrace();
			recording.enable("jdk.JavaErrorThrow").withStackTrace();
			recording.enable("jdk.ExceptionStatistics").withPeriod(Duration.ofMillis(100));
			recording.enable("jdk.FileRead").withThreshold(Duration.ZERO).withStackTrace();
			recording.enable("jdk.FileWrite").withThreshold(Duration.ZERO).withStackTrace();
			recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ZERO).withStackTrace();
			recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
			recording.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(100));
			recording.start();
			throwSome();
			doFileIo(dir);
			contendMonitor();
			recording.stop();
			recording.dump(out);
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static void throwSome() {
		for (int i = 0; i < 50; i++) {
			try {
				throw new IllegalArgumentException("falha sintética " + i);
			} catch (IllegalArgumentException expected) {
				// gerada de propósito para o evento jdk.JavaExceptionThrow
			}
		}
	}

	private static void doFileIo(Path dir) throws IOException {
		Path file = dir.resolve("io.bin");
		byte[] data = new byte[256 * 1024];
		for (int i = 0; i < 5; i++) {
			Files.write(file, data);
			Files.readAllBytes(file);
		}
	}

	private static void contendMonitor() throws InterruptedException {
		CountDownLatch holding = new CountDownLatch(1);
		Thread holder = new Thread(() -> {
			synchronized (LOCK) {
				holding.countDown();
				busyWait(Duration.ofMillis(300));
			}
		}, "synthetic-lock-holder");
		holder.start();
		holding.await();
		synchronized (LOCK) {
			busyWait(Duration.ofMillis(1));
		}
		holder.join();
	}

	private static void busyWait(Duration duration) {
		long end = System.nanoTime() + duration.toNanos();
		long counter = 0;
		while (System.nanoTime() < end) {
			counter++;
		}
		if (counter < 0) {
			throw new IllegalStateException();
		}
	}
}
