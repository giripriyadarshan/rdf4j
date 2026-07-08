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

import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.TupleQueryResultHandlerException;

/**
 * {@link TupleQuery} decorator that traces query evaluation. The streaming {@link #evaluate()} span stays open until
 * the result is closed/exhausted; the handler-based variant is traced as a single synchronous span.
 */
final class InstrumentedTupleQuery extends AbstractInstrumentedQuery<TupleQuery> implements TupleQuery {

	InstrumentedTupleQuery(TupleQuery delegate, OperationInstrumenter instrumenter, Rdf4jRequest request) {
		super(delegate, instrumenter, request);
	}

	@Override
	public TupleQueryResult evaluate() throws QueryEvaluationException {
		return instrumenter.traceTupleQuery(request, delegate::evaluate);
	}

	@Override
	public void evaluate(TupleQueryResultHandler handler)
			throws QueryEvaluationException, TupleQueryResultHandlerException {
		instrumenter.traceRunnable(request, () -> delegate.evaluate(handler));
	}
}
