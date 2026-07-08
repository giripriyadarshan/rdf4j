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
package org.eclipse.rdf4j.http.client.observability;

import org.eclipse.rdf4j.common.annotation.Experimental;
import org.eclipse.rdf4j.http.client.spi.HttpRequest;

/**
 * Service provider interface allowing optional libraries to inspect and enrich outgoing HTTP requests just before they
 * are sent by {@code SPARQLProtocolSession}. The primary use case is distributed-trace context propagation (e.g.
 * injecting W3C {@code traceparent} headers so a remote SPARQL endpoint can join the caller's trace).
 * <p>
 * Implementations are discovered once through {@link java.util.ServiceLoader} (registered via
 * {@code META-INF/services/org.eclipse.rdf4j.http.client.observability.HttpRequestListener}). When no implementations
 * are present on the classpath, requests are sent unchanged and the discovery hook is effectively free.
 * <p>
 * Implementations MUST NOT throw; failures are to be handled internally so that observability concerns never break
 * remote access.
 */
@Experimental
public interface HttpRequestListener {

	/**
	 * Called just before the given request is sent, allowing headers to be added (e.g. via
	 * {@link HttpRequest#setHeader(String, String)}).
	 *
	 * @param request the request about to be sent
	 */
	void beforeSend(HttpRequest request);
}
