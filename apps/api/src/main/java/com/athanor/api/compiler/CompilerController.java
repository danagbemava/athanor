package com.athanor.api.compiler;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scenarios")
public class CompilerController {

	private final CompilerService compilerService;

	public CompilerController(CompilerService compilerService) {
		this.compilerService = compilerService;
	}

	@PostMapping("/{id}/compile")
	public ResponseEntity<CompilerService.CompilationResult> compileScenario(
		@PathVariable("id") UUID scenarioId
	) {
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(compilerService.compileLatestScenario(scenarioId));
	}
}
