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
package org.eclipse.rdf4j.sail.observability;

import org.eclipse.rdf4j.common.annotation.Experimental;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailConnection;

/**
 * Service provider interface allowing optional libraries to transparently decorate every {@link SailConnection} handed
 * out by a {@link org.eclipse.rdf4j.sail.helpers.AbstractSail}-based store, without any code changes in the
 * application. The primary use case is observability instrumentation (e.g. OpenTelemetry spans for query evaluation
 * and transactions).
 * <p>
 * Implementations are discovered once through {@link java.util.ServiceLoader} (registered via
 * {@code META-INF/services/org.eclipse.rdf4j.sail.observability.SailConnectionDecorator}). When no implementations are
 * present on the classpath, connections are returned unchanged and the discovery hook is effectively free.
 * <p>
 * Implementation contract:
 * <ul>
 * <li>a decorator that is disabled by its own configuration MUST return the supplied connection unchanged;</li>
 * <li>if the supplied connection implements {@link NotifyingSailConnection}, the returned connection MUST also
 * implement it (stores based on {@code AbstractNotifyingSail} cast the returned connection to that type);</li>
 * <li>the returned connection MUST delegate {@link SailConnection#close()} to the supplied connection, as internal
 * connection tracking is keyed on the undecorated instance;</li>
 * <li>decorators MUST NOT throw from {@link #decorate}; failures are to be handled internally so that observability
 * concerns never break store access.</li>
 * </ul>
 */
@Experimental
public interface SailConnectionDecorator {

	/**
	 * Optionally decorates a connection that is about to be handed out by the given sail.
	 *
	 * @param sail       the sail that created the connection
	 * @param connection the connection to decorate
	 * @return the decorated connection, or the supplied connection unchanged if this decorator is disabled or not
	 *         applicable
	 */
	SailConnection decorate(Sail sail, SailConnection connection);
}
