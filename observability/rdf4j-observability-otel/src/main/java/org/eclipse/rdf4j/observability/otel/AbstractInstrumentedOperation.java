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

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.Operation;

/**
 * Base decorator that forwards every {@link Operation} configuration method to the wrapped operation. Subclasses add
 * the execution methods, wrapping them in spans through the shared {@link OperationInstrumenter} and
 * {@link Rdf4jRequest}.
 *
 * @param <O> the concrete operation type being wrapped
 */
abstract class AbstractInstrumentedOperation<O extends Operation> implements Operation {

	final O delegate;
	final OperationInstrumenter instrumenter;
	final Rdf4jRequest request;

	AbstractInstrumentedOperation(O delegate, OperationInstrumenter instrumenter, Rdf4jRequest request) {
		this.delegate = delegate;
		this.instrumenter = instrumenter;
		this.request = request;
	}

	@Override
	public void setBinding(String name, Value value) {
		delegate.setBinding(name, value);
	}

	@Override
	public void removeBinding(String name) {
		delegate.removeBinding(name);
	}

	@Override
	public void clearBindings() {
		delegate.clearBindings();
	}

	@Override
	public BindingSet getBindings() {
		return delegate.getBindings();
	}

	@Override
	public void setDataset(Dataset dataset) {
		delegate.setDataset(dataset);
	}

	@Override
	public Dataset getDataset() {
		return delegate.getDataset();
	}

	@Override
	public void setIncludeInferred(boolean includeInferred) {
		delegate.setIncludeInferred(includeInferred);
	}

	@Override
	public boolean getIncludeInferred() {
		return delegate.getIncludeInferred();
	}

	@Override
	public void setMaxExecutionTime(int maxExecutionTimeSeconds) {
		delegate.setMaxExecutionTime(maxExecutionTimeSeconds);
	}

	@Override
	public int getMaxExecutionTime() {
		return delegate.getMaxExecutionTime();
	}
}
