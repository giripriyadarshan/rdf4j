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

import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;

/**
 * {@link GraphQuery} decorator that traces query evaluation. The streaming {@link #evaluate()} span stays open until
 * the result is closed/exhausted; the handler-based variant is traced as a single synchronous span.
 */
final class InstrumentedGraphQuery extends AbstractInstrumentedQuery<GraphQuery> implements GraphQuery {

	InstrumentedGraphQuery(GraphQuery delegate, OperationInstrumenter instrumenter, Rdf4jRequest request) {
		super(delegate, instrumenter, request);
	}

	@Override
	public GraphQueryResult evaluate() throws QueryEvaluationException {
		return instrumenter.traceGraphQuery(request, delegate::evaluate);
	}

	@Override
	public void evaluate(RDFHandler handler) throws QueryEvaluationException, RDFHandlerException {
		instrumenter.traceRunnable(request, () -> delegate.evaluate(handler));
	}
}
