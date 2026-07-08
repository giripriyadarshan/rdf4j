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

import java.io.IOException;
import java.net.URI;

import org.eclipse.rdf4j.http.client.observability.RemoteOperation;
import org.eclipse.rdf4j.http.client.observability.RemoteOperationDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;

/**
 * Transparent, zero-code OpenTelemetry instrumentation for operations sent to remote SPARQL endpoints
 * ({@code SPARQLRepository}, {@code HTTPRepository}, federation members), registered through the
 * {@link RemoteOperationDecorator} service provider interface. With this artifact on the classpath, setting
 * {@code otel.instrumentation.rdf4j.enabled=true} is all that is needed to obtain database client spans — including the
 * (sanitized) SPARQL query text — for every remote query and update; spans are reported to {@link GlobalOpenTelemetry},
 * i.e. the OpenTelemetry Java agent's SDK when the agent is attached.
 * <p>
 * The remote-client layer can be individually disabled via {@code otel.instrumentation.rdf4j-client.enabled=false}.
 */
public final class OtelRemoteOperationDecorator implements RemoteOperationDecorator {

	private static final Logger logger = LoggerFactory.getLogger(OtelRemoteOperationDecorator.class);

	private volatile Rdf4jTelemetry telemetry;

	@Override
	public <T> T decorate(RemoteOperation operation, RemoteOperationExecution<T> execution) throws IOException {
		Rdf4jRequest request;
		OperationInstrumenter instrumenter;
		try {
			if (!RuntimeConfiguration.isClientInstrumentationEnabled()) {
				return execution.execute();
			}
			instrumenter = telemetry().instrumenter();
			request = requestFor(operation);
		} catch (RuntimeException e) {
			// observability must never break remote access
			logger.warn("Failed to instrument remote operation: {}", e.getMessage());
			logger.debug("Details: ", e);
			return execution.execute();
		}
		return instrumenter.traceRemote(request, execution::execute);
	}

	private static Rdf4jRequest requestFor(RemoteOperation operation) {
		SparqlOperation sparqlOperation = operationOf(operation);
		String serverAddress = null;
		Long serverPort = null;
		String namespace = operation.getEndpoint();
		if (operation.getEndpoint() != null) {
			try {
				URI endpoint = URI.create(operation.getEndpoint());
				serverAddress = endpoint.getHost();
				serverPort = endpoint.getPort() > 0 ? (long) endpoint.getPort() : null;
				// low-cardinality namespace: authority + path, without scheme or query parameters
				if (endpoint.getHost() != null) {
					namespace = endpoint.getAuthority() + (endpoint.getPath() != null ? endpoint.getPath() : "");
				}
			} catch (IllegalArgumentException e) {
				// leave the raw endpoint string as namespace
			}
		}
		return Rdf4jRequest.remote(sparqlOperation, operation.getQueryText(), namespace, serverAddress, serverPort);
	}

	private static SparqlOperation operationOf(RemoteOperation operation) {
		switch (operation.getKind()) {
		case TUPLE_QUERY:
			return SparqlOperation.SELECT;
		case GRAPH_QUERY:
			return SparqlOperation.graphQueryOf(operation.getQueryText());
		case BOOLEAN_QUERY:
			return SparqlOperation.ASK;
		case UPDATE:
		default:
			return SparqlOperation.UPDATE;
		}
	}

	private Rdf4jTelemetry telemetry() {
		Rdf4jTelemetry result = telemetry;
		if (result == null) {
			synchronized (this) {
				result = telemetry;
				if (result == null) {
					// resolved lazily so that an OpenTelemetry agent/SDK has been installed by the time
					// the first instrumented operation is executed
					result = Rdf4jTelemetry.create(GlobalOpenTelemetry.get());
					telemetry = result;
				}
			}
		}
		return result;
	}
}
