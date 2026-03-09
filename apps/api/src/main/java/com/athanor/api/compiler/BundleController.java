package com.athanor.api.compiler;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bundles")
public class BundleController {

	private final CompilerService compilerService;

	public BundleController(CompilerService compilerService) {
		this.compilerService = compilerService;
	}

	@GetMapping("/{bundleHash}")
	public BundleMetadata bundleMetadata(@PathVariable String bundleHash) {
		return compilerService.bundleMetadata(bundleHash);
	}

	@GetMapping(value = "/{bundleHash}/content", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> bundleContent(@PathVariable String bundleHash) {
		return ResponseEntity.ok(compilerService.bundleContent(bundleHash));
	}
}
