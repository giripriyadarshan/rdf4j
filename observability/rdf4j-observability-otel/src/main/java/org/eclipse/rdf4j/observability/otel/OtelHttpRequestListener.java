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

import org.eclipse.rdf4j.http.client.observability.HttpRequestListener;
import org.eclipse.rdf4j.http.client.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * Injects the current W3C trace context ({@code traceparent}/{@code tracestate}/{@code baggage}) into outgoing SPARQL
 * protocol requests, so remote endpoints (e.g. another RDF4J Server or a triplestore running its own OpenTelemetry
 * agent) can join the caller's distributed trace — without requiring an agent on the client side.
 * <p>
 * Only active when {@code otel.instrumentation.rdf4j.enabled=true} (and the {@code rdf4j-client} layer is not
 * individually disabled). When an OpenTelemetry Java agent also instruments the underlying HTTP client, the agent
 * overwrites these headers with its own (deeper) span context, which is equally correct.
 */
public final class OtelHttpRequestListener implements HttpRequestListener {

	private static final Logger logger = LoggerFactory.getLogger(OtelHttpRequestListener.class);

	private static final TextMapSetter<HttpRequest> SETTER = (request, name, value) -> {
		if (request != null) {
			request.setHeader(name, value);
		}
	};

	@Override
	public void beforeSend(HttpRequest request) {
		try {
			if (!RuntimeConfiguration.isClientInstrumentationEnabled()) {
				return;
			}
			GlobalOpenTelemetry.getPropagators()
					.getTextMapPropagator()
					.inject(Context.current(), request, SETTER);
		} catch (RuntimeException e) {
			// observability must never break remote access
			logger.warn("Failed to inject trace context into outgoing request: {}", e.getMessage());
			logger.debug("Details: ", e);
		}
	}
}
