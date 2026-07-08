/*******************************************************************************
 * Copyright (c) 2026 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.observability.otel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class Rdf4jServerTracingFilterTest {

	private static final AttributeKey<String> DB_NAMESPACE = AttributeKey.stringKey("db.namespace");
	private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
	private static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE = AttributeKey
			.longKey("http.response.status_code");

	private static final String INCOMING_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
	private static final String TRACEPARENT = "00-" + INCOMING_TRACE_ID + "-b7ad6b7169203331-01";

	private static InMemorySpanExporter spanExporter;
	private static OpenTelemetrySdk sdk;

	@BeforeAll
	static void setUp() {
		GlobalOpenTelemetry.resetForTest();
		spanExporter = InMemorySpanExporter.create();
		sdk = OpenTelemetrySdk.builder()
				.setTracerProvider(SdkTracerProvider.builder()
						.addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
						.build())
				.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
				.build();
		GlobalOpenTelemetry.set(sdk);
	}

	@AfterAll
	static void tearDown() {
		System.clearProperty(RuntimeConfiguration.ENABLED_PROPERTY);
		if (sdk != null) {
			sdk.close();
		}
		GlobalOpenTelemetry.resetForTest();
	}

	@AfterEach
	void cleanUp() {
		System.clearProperty(RuntimeConfiguration.ENABLED_PROPERTY);
		spanExporter.reset();
	}

	@Test
	void createsServerSpanContinuingIncomingTrace() throws Exception {
		System.setProperty(RuntimeConfiguration.ENABLED_PROPERTY, "true");
		HttpServletRequest request = request("POST", "/rdf4j-server/repositories/mem-rdf/transactions/txn-123",
				TRACEPARENT);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(response.getStatus()).thenReturn(200);
		FilterChain chain = mock(FilterChain.class);

		new Rdf4jServerTracingFilter().doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		List<SpanData> spans = spanExporter.getFinishedSpanItems();
		assertThat(spans).hasSize(1);
		SpanData span = spans.get(0);
		assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
		assertThat(span.getName()).isEqualTo("POST /repositories/{repositoryId}/transactions/{transactionId}");
		assertThat(span.getTraceId()).isEqualTo(INCOMING_TRACE_ID);
		assertThat(span.getParentSpanContext().isRemote()).isTrue();
		assertThat(span.getAttributes().get(DB_NAMESPACE)).isEqualTo("mem-rdf");
		assertThat(span.getAttributes().get(HTTP_ROUTE))
				.isEqualTo("/repositories/{repositoryId}/transactions/{transactionId}");
		assertThat(span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
	}

	@Test
	void enrichesExistingLocalServerSpanInsteadOfDuplicating() throws Exception {
		System.setProperty(RuntimeConfiguration.ENABLED_PROPERTY, "true");
		HttpServletRequest request = request("GET", "/rdf4j-server/repositories/mem-rdf", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(response.getStatus()).thenReturn(200);
		FilterChain chain = mock(FilterChain.class);

		// simulate an agent-created SERVER span already being active
		Span agentSpan = sdk.getTracer("agent").spanBuilder("GET").setSpanKind(SpanKind.SERVER).startSpan();
		try (Scope ignored = agentSpan.makeCurrent()) {
			new Rdf4jServerTracingFilter().doFilter(request, response, chain);
		} finally {
			agentSpan.end();
		}

		verify(chain).doFilter(request, response);
		List<SpanData> spans = spanExporter.getFinishedSpanItems();
		// no duplicate span; the existing one carries the repository id
		assertThat(spans).hasSize(1);
		assertThat(spans.get(0).getAttributes().get(DB_NAMESPACE)).isEqualTo("mem-rdf");
	}

	@Test
	void doesNothingWhenFlagIsUnset() throws Exception {
		HttpServletRequest request = request("GET", "/rdf4j-server/repositories/mem-rdf", TRACEPARENT);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		new Rdf4jServerTracingFilter().doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
	}

	private static HttpServletRequest request(String method, String uri, String traceparent) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn(method);
		when(request.getRequestURI()).thenReturn(uri);
		when(request.getContextPath()).thenReturn("/rdf4j-server");
		when(request.getHeaderNames())
				.thenReturn(Collections.enumeration(
						traceparent != null ? List.of("traceparent") : Collections.emptyList()));
		when(request.getHeader("traceparent")).thenReturn(traceparent);
		return request;
	}
}
