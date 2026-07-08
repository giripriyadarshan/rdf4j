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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SparqlSanitizerTest {

	@Test
	void redactsStringLiterals() {
		String sanitized = SparqlSanitizer.sanitize("SELECT ?s WHERE { ?s <http://ex/name> \"secret-name\" }");
		assertThat(sanitized)
				.doesNotContain("secret-name")
				.contains("SELECT ?s WHERE")
				.contains("<http://ex/name>")
				.contains("?");
	}

	@Test
	void redactsNumericLiterals() {
		String sanitized = SparqlSanitizer.sanitize("SELECT ?s WHERE { ?s <http://ex/age> 42 } LIMIT 10");
		assertThat(sanitized).doesNotContain("42").doesNotContain("10");
	}

	@Test
	void redactsTripleQuotedLiterals() {
		String sanitized = SparqlSanitizer.sanitize("SELECT ?s WHERE { ?s ?p \"\"\"multi\nline secret\"\"\" }");
		assertThat(sanitized).doesNotContain("secret").doesNotContain("multi");
	}

	@Test
	void preservesVariablesAndKeywords() {
		String sanitized = SparqlSanitizer.sanitize("SELECT ?x1 ?y2 WHERE { ?x1 ?y2 ?z3 }");
		assertThat(sanitized).isEqualTo("SELECT ?x1 ?y2 WHERE { ?x1 ?y2 ?z3 }");
	}

	@Test
	void handlesNullAndEmpty() {
		assertThat(SparqlSanitizer.sanitize(null)).isNull();
		assertThat(SparqlSanitizer.sanitize("")).isEmpty();
	}
}
