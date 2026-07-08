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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQueryResult;

import io.opentelemetry.context.Context;

/**
 * A {@link TupleQueryResult} decorator that keeps the query span open for the (lazily evaluated) lifetime of the result
 * and ends it exactly once — on {@link #close()}, on natural exhaustion, or with an error status if iteration throws.
 * It also counts returned rows for the {@code db.response.returned_rows} attribute.
 */
final class SpanEndingTupleQueryResult implements TupleQueryResult {

	private final TupleQueryResult delegate;
	private final OperationInstrumenter instrumenter;
	private final Context context;
	private final Rdf4jRequest request;
	private final AtomicBoolean ended = new AtomicBoolean(false);
	private long rows;

	SpanEndingTupleQueryResult(TupleQueryResult delegate, OperationInstrumenter instrumenter, Context context,
			Rdf4jRequest request) {
		this.delegate = delegate;
		this.instrumenter = instrumenter;
		this.context = context;
		this.request = request;
	}

	@Override
	public List<String> getBindingNames() throws QueryEvaluationException {
		return delegate.getBindingNames();
	}

	@Override
	public boolean hasNext() throws QueryEvaluationException {
		try {
			boolean hasNext = delegate.hasNext();
			if (!hasNext) {
				endOk();
			}
			return hasNext;
		} catch (Throwable t) {
			endError(t);
			throw t;
		}
	}

	@Override
	public BindingSet next() throws QueryEvaluationException {
		try {
			BindingSet next = delegate.next();
			rows++;
			return next;
		} catch (Throwable t) {
			endError(t);
			throw t;
		}
	}

	@Override
	public void remove() throws QueryEvaluationException {
		delegate.remove();
	}

	@Override
	public void close() throws QueryEvaluationException {
		try {
			delegate.close();
		} catch (Throwable t) {
			endError(t);
			throw t;
		} finally {
			endOk();
		}
	}

	private void endOk() {
		if (ended.compareAndSet(false, true)) {
			instrumenter.end(context, request, Rdf4jResponse.ofRows(rows), null);
		}
	}

	private void endError(Throwable error) {
		if (ended.compareAndSet(false, true)) {
			instrumenter.end(context, request, Rdf4jResponse.ofRows(rows), error);
		}
	}
}
