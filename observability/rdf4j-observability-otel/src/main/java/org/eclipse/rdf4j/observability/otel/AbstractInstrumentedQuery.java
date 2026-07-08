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

import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.explanation.Explanation;

/**
 * Base decorator for the {@link Query} types, adding forwarding of the query-specific configuration and explain methods
 * on top of {@link AbstractInstrumentedOperation}.
 *
 * @param <Q> the concrete query type being wrapped
 */
abstract class AbstractInstrumentedQuery<Q extends Query> extends AbstractInstrumentedOperation<Q> implements Query {

	AbstractInstrumentedQuery(Q delegate, OperationInstrumenter instrumenter, Rdf4jRequest request) {
		super(delegate, instrumenter, request);
	}

	@Deprecated(since = "2.0")
	@Override
	public void setMaxQueryTime(int maxQueryTime) {
		delegate.setMaxQueryTime(maxQueryTime);
	}

	@Deprecated(since = "2.0")
	@Override
	public int getMaxQueryTime() {
		return delegate.getMaxQueryTime();
	}

	@Override
	public Explanation explain(Explanation.Level level) {
		return delegate.explain(level);
	}
}
