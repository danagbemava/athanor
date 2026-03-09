package com.athanor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WebConfigIntegrationTests {

	@LocalServerPort
	private int port;

	@Test
	void preflightAllowsLocalUiOrigins() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + port + "/scenarios/test/simulate"))
			.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
			.header("Origin", "http://127.0.0.1:3002")
			.header("Access-Control-Request-Method", "POST")
			.build();

		HttpResponse<Void> response = HttpClient
			.newHttpClient()
			.send(request, HttpResponse.BodyHandlers.discarding());

		assertEquals(200, response.statusCode());
		assertEquals(
			"http://127.0.0.1:3002",
			response.headers().firstValue("access-control-allow-origin").orElseThrow()
		);
		assertTrue(
			response
				.headers()
				.firstValue("access-control-allow-methods")
				.orElseThrow()
				.contains("POST")
		);
	}

	@Test
	void prometheusEndpointExposesQueueDepthMetric() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus"))
			.GET()
			.build();

		HttpResponse<String> response = HttpClient
			.newHttpClient()
			.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("athanor_jobs_queue_depth"));
	}
}
