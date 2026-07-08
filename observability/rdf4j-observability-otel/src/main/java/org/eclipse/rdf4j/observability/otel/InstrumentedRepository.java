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

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.base.RepositoryWrapper;

/**
 * A {@link Repository} decorator whose {@link #getConnection()} returns an {@link InstrumentedRepositoryConnection}, so
 * every connection obtained from it emits OpenTelemetry spans.
 */
final class InstrumentedRepository extends RepositoryWrapper {

	private final Rdf4jTelemetry telemetry;
	private final String namespace;

	InstrumentedRepository(Repository delegate, Rdf4jTelemetry telemetry, String namespace) {
		super(delegate);
		this.telemetry = telemetry;
		this.namespace = namespace;
	}

	@Override
	public RepositoryConnection getConnection() throws RepositoryException {
		return new InstrumentedRepositoryConnection(this, getDelegate().getConnection(), telemetry, namespace);
	}
}
