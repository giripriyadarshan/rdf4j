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

/**
 * The kind of RDF4J operation an OpenTelemetry span represents. The {@link #name()} is used verbatim as the
 * {@code db.operation.name} attribute and is always derived from the RDF4J query/update object type known at
 * prepare-time; it is never parsed out of the query text.
 */
enum SparqlOperation {

	SELECT,
	CONSTRUCT,
	DESCRIBE,
	ASK,
	UPDATE,
	/** Engine-level query evaluation observed at the {@code SailConnection} layer (no SPARQL text available). */
	EVALUATE,
	BEGIN,
	COMMIT,
	ROLLBACK;

	/**
	 * Best-effort disambiguation of a graph query into {@link #CONSTRUCT} or {@link #DESCRIBE} by inspecting the first
	 * significant SPARQL keyword. Both query forms return RDF graphs, but RDF4J exposes them through the same
	 * {@code GraphQuery} type, so the concrete form is only visible from the query text. Defaults to
	 * {@link #CONSTRUCT}.
	 */
	static SparqlOperation graphQueryOf(String queryText) {
		if (queryText == null) {
			return CONSTRUCT;
		}
		int i = 0;
		int n = queryText.length();
		while (i < n) {
			char c = queryText.charAt(i);
			// Skip whitespace.
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			// Skip line comments.
			if (c == '#') {
				while (i < n && queryText.charAt(i) != '\n') {
					i++;
				}
				continue;
			}
			// Skip PREFIX / BASE prologue declarations.
			if ((c == 'p' || c == 'P') && matchesKeyword(queryText, i, "PREFIX")) {
				i = skipPrologueLine(queryText, i);
				continue;
			}
			if ((c == 'b' || c == 'B') && matchesKeyword(queryText, i, "BASE")) {
				i = skipPrologueLine(queryText, i);
				continue;
			}
			if ((c == 'd' || c == 'D') && matchesKeyword(queryText, i, "DESCRIBE")) {
				return DESCRIBE;
			}
			// Anything else (typically CONSTRUCT) is treated as CONSTRUCT.
			return CONSTRUCT;
		}
		return CONSTRUCT;
	}

	private static int skipPrologueLine(String s, int from) {
		int i = from;
		int n = s.length();
		while (i < n && s.charAt(i) != '\n') {
			i++;
		}
		return i;
	}

	private static boolean matchesKeyword(String s, int from, String keyword) {
		int end = from + keyword.length();
		if (end > s.length()) {
			return false;
		}
		return s.regionMatches(true, from, keyword, 0, keyword.length());
	}
}
