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

import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.QueryEvaluationException;

/**
 * {@link BooleanQuery} (ASK) decorator that traces evaluation as a single synchronous span.
 */
final class InstrumentedBooleanQuery extends AbstractInstrumentedQuery<BooleanQuery> implements BooleanQuery {

	InstrumentedBooleanQuery(BooleanQuery delegate, OperationInstrumenter instrumenter, Rdf4jRequest request) {
		super(delegate, instrumenter, request);
	}

	@Override
	public boolean evaluate() throws QueryEvaluationException {
		return instrumenter.traceSupplier(request, delegate::evaluate);
	}
}
