package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

/** Identificadores dos tipos de evento JFR usados pela extração. */
public final class JfrEventTypes {

	public static final String JVM_INFORMATION = "jdk.JVMInformation";
	public static final String OS_INFORMATION = "jdk.OSInformation";
	public static final String CPU_INFORMATION = "jdk.CPUInformation";
	public static final String PHYSICAL_MEMORY = "jdk.PhysicalMemory";
	public static final String CPU_LOAD = "jdk.CPULoad";
	public static final String THREAD_CPU_LOAD = "jdk.ThreadCPULoad";
	public static final String HEAP_SUMMARY = "jdk.GCHeapSummary";
	public static final String HEAP_CONFIGURATION = "jdk.GCHeapConfiguration";
	public static final String GC_CONFIGURATION = "jdk.GCConfiguration";
	public static final String GARBAGE_COLLECTION = "jdk.GarbageCollection";
	public static final String THREAD_STATISTICS = "jdk.JavaThreadStatistics";
	public static final String THREAD_START = "jdk.ThreadStart";
	public static final String THREAD_END = "jdk.ThreadEnd";
	public static final String MONITOR_ENTER = "jdk.JavaMonitorEnter";
	public static final String MONITOR_WAIT = "jdk.JavaMonitorWait";
	public static final String THREAD_PARK = "jdk.ThreadPark";
	public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
	public static final String ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample";
	public static final String ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB";
	public static final String ALLOCATION_OUTSIDE_TLAB = "jdk.ObjectAllocationOutsideTLAB";
	public static final String EXCEPTION_THROW = "jdk.JavaExceptionThrow";
	public static final String ERROR_THROW = "jdk.JavaErrorThrow";
	public static final String EXCEPTION_STATISTICS = "jdk.ExceptionStatistics";
	public static final String FILE_READ = "jdk.FileRead";
	public static final String FILE_WRITE = "jdk.FileWrite";
	public static final String SOCKET_READ = "jdk.SocketRead";
	public static final String SOCKET_WRITE = "jdk.SocketWrite";
	public static final String OLD_OBJECT_SAMPLE = "jdk.OldObjectSample";
	public static final String ACTIVE_SETTING = "jdk.ActiveSetting";

	private JfrEventTypes() {
	}
}
