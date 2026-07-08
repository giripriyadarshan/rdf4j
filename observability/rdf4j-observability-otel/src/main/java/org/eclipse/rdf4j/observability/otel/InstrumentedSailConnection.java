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

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.SailConnectionWrapper;

/**
 * A {@link SailConnection} decorator that emits OpenTelemetry spans for engine-level query evaluation and transaction
 * boundaries. Installed transparently by {@link OtelSailConnectionDecorator} when the instrumentation is enabled.
 */
final class InstrumentedSailConnection extends SailConnectionWrapper {

	private final OperationInstrumenter instrumenter;
	private final String namespace;

	InstrumentedSailConnection(SailConnection wrappedCon, OperationInstrumenter instrumenter, String namespace) {
		super(wrappedCon);
		this.instrumenter = instrumenter;
		this.namespace = namespace;
	}

	@Override
	public CloseableIteration<? extends BindingSet> evaluate(TupleExpr tupleExpr, Dataset dataset,
			BindingSet bindings, boolean includeInferred) throws SailException {
		return instrumenter.<BindingSet>traceIteration(
				Rdf4jRequest.transaction(SparqlOperation.EVALUATE, namespace),
				() -> super.evaluate(tupleExpr, dataset, bindings, includeInferred));
	}

	@Override
	public void begin() throws SailException {
		instrumenter.traceRunnable(Rdf4jRequest.transaction(SparqlOperation.BEGIN, namespace), super::begin);
	}

	@Override
	public void begin(IsolationLevel level) throws SailException {
		instrumenter.traceRunnable(Rdf4jRequest.transaction(SparqlOperation.BEGIN, namespace),
				() -> super.begin(level));
	}

	@Override
	public void commit() throws SailException {
		instrumenter.traceRunnable(Rdf4jRequest.transaction(SparqlOperation.COMMIT, namespace), super::commit);
	}

	@Override
	public void rollback() throws SailException {
		instrumenter.traceRunnable(Rdf4jRequest.transaction(SparqlOperation.ROLLBACK, namespace), super::rollback);
	}
}
