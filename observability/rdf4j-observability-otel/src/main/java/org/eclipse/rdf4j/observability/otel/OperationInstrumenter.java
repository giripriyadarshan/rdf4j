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

import java.io.IOException;
import java.util.function.Supplier;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResult;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/**
 * Wraps an OpenTelemetry {@link Instrumenter} and adapts it to RDF4J's two execution shapes:
 * <ul>
 * <li><b>synchronous</b> operations (boolean queries, updates, transaction boundaries, handler-based evaluation) where
 * the span spans the whole method call; and</li>
 * <li><b>streaming</b> operations (tuple/graph query results) where the span must stay open until the returned
 * {@code CloseableIteration} is closed or exhausted.</li>
 * </ul>
 * When the underlying {@code OpenTelemetry} is a no-op, {@link Instrumenter#shouldStart} short-circuits and the
 * original result is returned without any wrapping, so a disabled instance adds effectively zero overhead.
 * <p>
 * All RDF4J query/update/repository exceptions are unchecked, so error propagation needs no checked-exception plumbing.
 */
final class OperationInstrumenter {

	private final Instrumenter<Rdf4jRequest, Rdf4jResponse> instrumenter;

	OperationInstrumenter(Instrumenter<Rdf4jRequest, Rdf4jResponse> instrumenter) {
		this.instrumenter = instrumenter;
	}

	/** Traces a synchronous operation that produces a value. */
	<T> T traceSupplier(Rdf4jRequest request, Supplier<T> body) {
		Context parentContext = Context.current();
		if (!instrumenter.shouldStart(parentContext, request)) {
			return body.get();
		}
		Context context = instrumenter.start(parentContext, request);
		Throwable error = null;
		try (Scope ignored = context.makeCurrent()) {
			return body.get();
		} catch (Throwable t) {
			error = t;
			throw t;
		} finally {
			instrumenter.end(context, request, Rdf4jResponse.EMPTY, error);
		}
	}

	/** Traces a synchronous operation that produces no value. */
	void traceRunnable(Rdf4jRequest request, Runnable body) {
		traceSupplier(request, () -> {
			body.run();
			return null;
		});
	}

	/** Traces a tuple query whose span ends when the result iteration is closed or exhausted. */
	TupleQueryResult traceTupleQuery(Rdf4jRequest request, Supplier<TupleQueryResult> body) {
		Context parentContext = Context.current();
		if (!instrumenter.shouldStart(parentContext, request)) {
			return body.get();
		}
		Context context = instrumenter.start(parentContext, request);
		TupleQueryResult result;
		try (Scope ignored = context.makeCurrent()) {
			result = body.get();
		} catch (Throwable t) {
			instrumenter.end(context, request, Rdf4jResponse.EMPTY, t);
			throw t;
		}
		return new SpanEndingTupleQueryResult(result, this, context, request);
	}

	/** Traces a streaming operation whose span ends when the returned iteration is closed or exhausted. */
	<T> CloseableIteration<T> traceIteration(Rdf4jRequest request,
			Supplier<? extends CloseableIteration<? extends T>> body) {
		Context parentContext = Context.current();
		if (!instrumenter.shouldStart(parentContext, request)) {
			@SuppressWarnings("unchecked")
			CloseableIteration<T> result = (CloseableIteration<T>) body.get();
			return result;
		}
		Context context = instrumenter.start(parentContext, request);
		CloseableIteration<? extends T> result;
		try (Scope ignored = context.makeCurrent()) {
			result = body.get();
		} catch (Throwable t) {
			instrumenter.end(context, request, Rdf4jResponse.EMPTY, t);
			throw t;
		}
		return new SpanEndingCloseableIteration<>(result, this, context, request);
	}

	/** Traces a graph query whose span ends when the result iteration is closed or exhausted. */
	GraphQueryResult traceGraphQuery(Rdf4jRequest request, Supplier<GraphQueryResult> body) {
		Context parentContext = Context.current();
		if (!instrumenter.shouldStart(parentContext, request)) {
			return body.get();
		}
		Context context = instrumenter.start(parentContext, request);
		GraphQueryResult result;
		try (Scope ignored = context.makeCurrent()) {
			result = body.get();
		} catch (Throwable t) {
			instrumenter.end(context, request, Rdf4jResponse.EMPTY, t);
			throw t;
		}
		return new SpanEndingGraphQueryResult(result, this, context, request);
	}

	/** Ends a span previously started for a streaming result. */
	void end(Context context, Rdf4jRequest request, Rdf4jResponse response, Throwable error) {
		instrumenter.end(context, request, response, error);
	}

	/** A result supplier that may perform I/O. */
	@FunctionalInterface
	interface IoSupplier<T> {

		T get() throws IOException;
	}

	/**
	 * Traces a remote protocol operation. If the result is a streaming query result, the span stays open until the
	 * result is closed or exhausted; otherwise the span ends when the operation returns.
	 */
	@SuppressWarnings("unchecked")
	<T> T traceRemote(Rdf4jRequest request, IoSupplier<T> body) throws IOException {
		Context parentContext = Context.current();
		if (!instrumenter.shouldStart(parentContext, request)) {
			return body.get();
		}
		Context context = instrumenter.start(parentContext, request);
		T result;
		try (Scope ignored = context.makeCurrent()) {
			result = body.get();
		} catch (Throwable t) {
			instrumenter.end(context, request, Rdf4jResponse.EMPTY, t);
			throw t;
		}
		if (result instanceof TupleQueryResult) {
			return (T) new SpanEndingTupleQueryResult((TupleQueryResult) result, this, context, request);
		}
		if (result instanceof GraphQueryResult) {
			return (T) new SpanEndingGraphQueryResult((GraphQueryResult) result, this, context, request);
		}
		instrumenter.end(context, request, Rdf4jResponse.EMPTY, null);
		return result;
	}
}
