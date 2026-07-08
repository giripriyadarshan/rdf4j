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

import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.query.UpdateExecutionException;

/**
 * {@link Update} decorator that traces execution as a single synchronous span.
 */
final class InstrumentedUpdate extends AbstractInstrumentedOperation<Update> implements Update {

	InstrumentedUpdate(Update delegate, OperationInstrumenter instrumenter, Rdf4jRequest request) {
		super(delegate, instrumenter, request);
	}

	@Override
	public void execute() throws UpdateExecutionException {
		instrumenter.traceRunnable(request, delegate::execute);
	}
}
