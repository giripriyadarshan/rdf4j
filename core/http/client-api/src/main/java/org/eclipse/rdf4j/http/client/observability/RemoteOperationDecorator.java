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

import java.io.IOException;

import org.eclipse.rdf4j.common.annotation.Experimental;

/**
 * Service provider interface allowing optional libraries to transparently observe (and time) SPARQL protocol
 * operations sent to remote endpoints by {@code SPARQLProtocolSession}, without any code changes in the application.
 * The primary use case is observability instrumentation (e.g. OpenTelemetry spans carrying the SPARQL query text for
 * remote query/update execution).
 * <p>
 * Implementations are discovered once through {@link java.util.ServiceLoader} (registered via
 * {@code META-INF/services/org.eclipse.rdf4j.http.client.observability.RemoteOperationDecorator}). When no
 * implementations are present on the classpath, operations execute unchanged and the discovery hook is effectively
 * free.
 * <p>
 * Implementation contract:
 * <ul>
 * <li>a decorator that is disabled by its own configuration MUST invoke the execution unchanged and return its
 * result;</li>
 * <li>decorators MAY substitute the result with a delegating wrapper of the same runtime interface (e.g. to observe a
 * lazily consumed query result), but MUST preserve behavior;</li>
 * <li>failures internal to the decorator MUST NOT propagate to the caller; observability concerns never break remote
 * access.</li>
 * </ul>
 */
@Experimental
public interface RemoteOperationDecorator {

	/**
	 * A single remote operation execution, to be invoked (exactly once) by the decorator.
	 *
	 * @param <T> the result type of the operation
	 */
	@FunctionalInterface
	interface RemoteOperationExecution<T> {

		T execute() throws IOException;
	}

	/**
	 * Decorates the execution of a remote SPARQL protocol operation.
	 *
	 * @param operation description of the operation about to be executed
	 * @param execution the operation itself; must be invoked exactly once
	 * @return the operation result (possibly substituted with a behavior-preserving delegating wrapper)
	 * @throws IOException if the execution throws it
	 */
	<T> T decorate(RemoteOperation operation, RemoteOperationExecution<T> execution) throws IOException;
}
