package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.SoftwareResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.SoftwareUpdateRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AnalysisBatch;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.Software;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.NotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.RetentionProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.AnalysisBatchRepository;
import io.github.maiconthales.jmc_ai_analyzer_api.system.repository.SoftwareRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de software: criação automática no upload, consulta e alteração de retenção e pacotes. */
@Service
@RequiredArgsConstructor
public class SoftwareService {

	static final String RESOURCE = "Software";

	private final SoftwareRepository softwareRepository;
	private final AnalysisBatchRepository batchRepository;
	private final RetentionProperties retention;

	/** Cria o software numa transação própria, para que conflito de nome não derrube o upload. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Software create(String name) {
		return softwareRepository.saveAndFlush(new Software(name, retention.defaultDays()));
	}

	@Transactional(readOnly = true)
	public Optional<Software> find(String name) {
		return softwareRepository.findByName(name);
	}

	@Transactional(readOnly = true)
	public List<SoftwareResponse> list() {
		return softwareRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
	}

	@Transactional
	public SoftwareResponse update(String name, SoftwareUpdateRequest request) {
		Software software = softwareRepository.findByName(name).orElseThrow(() -> new NotFoundException(RESOURCE, name));
		if (request.retentionDays() != null) {
			software.setRetentionDays(request.retentionDays());
		}
		if (request.appPackages() != null) {
			software.replaceAppPackages(request.appPackages().stream().map(String::trim).distinct().toList());
		}
		return toResponse(softwareRepository.saveAndFlush(software));
	}

	private SoftwareResponse toResponse(Software software) {
		Optional<AnalysisBatch> last = batchRepository.findFirstBySoftwareIdOrderByCreatedAtDesc(software.getId());
		return new SoftwareResponse(software.getName(), software.getRetentionDays(), software.appPackageList(),
				batchRepository.countBySoftwareId(software.getId()),
				last.map(AnalysisBatch::getCreatedAt).orElse(null),
				last.map(AnalysisBatch::getSoftwareVersion).orElse(null));
	}
}
