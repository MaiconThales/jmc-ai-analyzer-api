package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Software ao qual os lotes pertencem; unidade de histórico e de retenção. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "software")
public class Software {

	@Id
	private UUID id;

	private String name;

	private int retentionDays;

	/** Prefixos de pacote da aplicação separados por vírgula. */
	private String appPackages;

	private Instant createdAt;

	private Instant updatedAt;

	public Software(String name, int retentionDays) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.retentionDays = retentionDays;
	}

	/** Prefixos de pacote da aplicação, sem vazios. */
	public List<String> appPackageList() {
		if (appPackages == null || appPackages.isBlank()) {
			return List.of();
		}
		return Arrays.stream(appPackages.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	public void replaceAppPackages(List<String> packages) {
		this.appPackages = packages == null || packages.isEmpty() ? null : String.join(",", packages);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof Software software && id != null && id.equals(software.id);
	}

	@Override
	public int hashCode() {
		return Software.class.hashCode();
	}
}
