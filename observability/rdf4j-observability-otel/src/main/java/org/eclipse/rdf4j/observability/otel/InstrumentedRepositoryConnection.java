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

import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.common.transaction.TransactionSetting;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.base.RepositoryConnectionWrapper;

/**
 * A {@link RepositoryConnection} decorator that emits OpenTelemetry spans for prepared queries/updates and transaction
 * boundaries. It works uniformly for any repository implementation — local ({@code SailRepository}) or remote
 * ({@code HTTPRepository}/{@code SPARQLRepository}) — because it wraps the public {@link RepositoryConnection} API
 * rather than any storage-specific type.
 * <p>
 * The operation type is taken from the query object requested at prepare-time and never parsed from the query text.
 * Only the three-argument {@code prepare*} methods are overridden; the string/language convenience overloads defined on
 * the interface delegate to these, so all of them are instrumented.
 */
final class InstrumentedRepositoryConnection extends RepositoryConnectionWrapper {

	private final Rdf4jTelemetry telemetry;
	private final String namespace;

	InstrumentedRepositoryConnection(Repository repository, RepositoryConnection delegate, Rdf4jTelemetry telemetry,
			String namespace) {
		super(repository, delegate);
		this.telemetry = telemetry;
		this.namespace = namespace;
	}

	private OperationInstrumenter instrumenter() {
		return telemetry.instrumenter();
	}

	@Override
	public TupleQuery prepareTupleQuery(QueryLanguage ql, String query, String baseURI)
			throws MalformedQueryException, RepositoryException {
		TupleQuery delegate = super.prepareTupleQuery(ql, query, baseURI);
		return new InstrumentedTupleQuery(delegate, instrumenter(),
				Rdf4jRequest.query(SparqlOperation.SELECT, query, namespace));
	}

	@Override
	public GraphQuery prepareGraphQuery(QueryLanguage ql, String query, String baseURI)
			throws MalformedQueryException, RepositoryException {
		GraphQuery delegate = super.prepareGraphQuery(ql, query, baseURI);
		return new InstrumentedGraphQuery(delegate, instrumenter(),
				Rdf4jRequest.query(SparqlOperation.graphQueryOf(query), query, namespace));
	}

	@Override
	public BooleanQuery prepareBooleanQuery(QueryLanguage ql, String query, String baseURI)
			throws MalformedQueryException, RepositoryException {
		BooleanQuery delegate = super.prepareBooleanQuery(ql, query, baseURI);
		return new InstrumentedBooleanQuery(delegate, instrumenter(),
				Rdf4jRequest.query(SparqlOperation.ASK, query, namespace));
	}

	@Override
	public Update prepareUpdate(QueryLanguage ql, String update, String baseURI)
			throws MalformedQueryException, RepositoryException {
		Update delegate = super.prepareUpdate(ql, update, baseURI);
		return new InstrumentedUpdate(delegate, instrumenter(),
				Rdf4jRequest.query(SparqlOperation.UPDATE, update, namespace));
	}

	@Override
	public Query prepareQuery(QueryLanguage ql, String query, String baseURI)
			throws MalformedQueryException, RepositoryException {
		Query delegate = super.prepareQuery(ql, query, baseURI);
		if (delegate instanceof TupleQuery) {
			return new InstrumentedTupleQuery((TupleQuery) delegate, instrumenter(),
					Rdf4jRequest.query(SparqlOperation.SELECT, query, namespace));
		}
		if (delegate instanceof GraphQuery) {
			return new InstrumentedGraphQuery((GraphQuery) delegate, instrumenter(),
					Rdf4jRequest.query(SparqlOperation.graphQueryOf(query), query, namespace));
		}
		if (delegate instanceof BooleanQuery) {
			return new InstrumentedBooleanQuery((BooleanQuery) delegate, instrumenter(),
					Rdf4jRequest.query(SparqlOperation.ASK, query, namespace));
		}
		return delegate;
	}

	@Override
	public void begin() throws RepositoryException {
		instrumenter().traceRunnable(Rdf4jRequest.transaction(SparqlOperation.BEGIN, namespace), super::begin);
	}

	@Override
	public void begin(IsolationLevel level) throws RepositoryException {
		instrumenter().traceRunnable(Rdf4jRequest.transaction(SparqlOperation.BEGIN, namespace),
				() -> super.begin(level));
	}

	@Override
	public void begin(TransactionSetting... settings) {
		instrumenter().traceRunnable(Rdf4jRequest.transaction(SparqlOperation.BEGIN, namespace),
				() -> super.begin(settings));
	}

	@Override
	public void commit() throws RepositoryException {
		instrumenter().traceRunnable(Rdf4jRequest.transaction(SparqlOperation.COMMIT, namespace), super::commit);
	}

	@Override
	public void rollback() throws RepositoryException {
		instrumenter().traceRunnable(Rdf4jRequest.transaction(SparqlOperation.ROLLBACK, namespace), super::rollback);
	}
}
