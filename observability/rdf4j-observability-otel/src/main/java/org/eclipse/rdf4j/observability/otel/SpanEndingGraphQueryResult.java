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

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.QueryEvaluationException;

import io.opentelemetry.context.Context;

/**
 * A {@link GraphQueryResult} decorator that keeps the query span open for the (lazily evaluated) lifetime of the result
 * and ends it exactly once — on {@link #close()}, on natural exhaustion, or with an error status if iteration throws.
 * It also counts returned statements for the {@code db.response.returned_rows} attribute.
 */
final class SpanEndingGraphQueryResult implements GraphQueryResult {

	private final GraphQueryResult delegate;
	private final OperationInstrumenter instrumenter;
	private final Context context;
	private final Rdf4jRequest request;
	private final AtomicBoolean ended = new AtomicBoolean(false);
	private long rows;

	SpanEndingGraphQueryResult(GraphQueryResult delegate, OperationInstrumenter instrumenter, Context context,
			Rdf4jRequest request) {
		this.delegate = delegate;
		this.instrumenter = instrumenter;
		this.context = context;
		this.request = request;
	}

	@Override
	public Map<String, String> getNamespaces() throws QueryEvaluationException {
		return delegate.getNamespaces();
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
	public Statement next() throws QueryEvaluationException {
		try {
			Statement next = delegate.next();
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
