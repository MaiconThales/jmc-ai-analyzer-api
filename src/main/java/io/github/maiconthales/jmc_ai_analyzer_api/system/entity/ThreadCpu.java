package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Thread com maior uso de CPU. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "thread_cpu")
public class ThreadCpu extends RecordingChild {

	private int rank;

	private String threadName;

	private double cpuAvg;

	private double cpuMax;
}
