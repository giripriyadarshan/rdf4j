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
package org.eclipse.rdf4j.http.client.observability;

import org.eclipse.rdf4j.common.annotation.Experimental;

/**
 * Immutable description of a SPARQL protocol operation that is about to be sent to a remote endpoint, passed to
 * {@link RemoteOperationDecorator}s.
 */
@Experimental
public final class RemoteOperation {

	/**
	 * The coarse kind of a remote operation, corresponding to the SPARQL protocol operation being invoked.
	 */
	public enum Kind {
		TUPLE_QUERY,
		GRAPH_QUERY,
		BOOLEAN_QUERY,
		UPDATE
	}

	private final Kind kind;
	private final String queryText;
	private final String endpoint;

	public RemoteOperation(Kind kind, String queryText, String endpoint) {
		this.kind = kind;
		this.queryText = queryText;
		this.endpoint = endpoint;
	}

	public Kind getKind() {
		return kind;
	}

	/**
	 * @return the SPARQL query or update text, or {@code null} if not available
	 */
	public String getQueryText() {
		return queryText;
	}

	/**
	 * @return the endpoint URL the operation is sent to, or {@code null} if not available
	 */
	public String getEndpoint() {
		return endpoint;
	}
}
