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
 * Best-effort redaction of literal values from a SPARQL query so that the {@code db.query.text} attribute keeps the
 * query <em>shape</em> without leaking the potentially sensitive values embedded in it (mirrors the behaviour of the
 * OpenTelemetry JDBC statement sanitizer).
 * <p>
 * String literals (single-, double- and triple-quoted) and numeric literals are replaced with a {@code ?} placeholder;
 * IRIs, variable names, keywords and structure are preserved. This is intentionally conservative and does not fully
 * parse SPARQL — it never throws and always returns a string.
 */
final class SparqlSanitizer {

	private SparqlSanitizer() {
	}

	static String sanitize(String query) {
		if (query == null || query.isEmpty()) {
			return query;
		}
		try {
			return doSanitize(query);
		} catch (RuntimeException e) {
			// A sanitizer must never break the caller; fall back to a fully redacted marker.
			return "?";
		}
	}

	private static String doSanitize(String s) {
		int n = s.length();
		StringBuilder out = new StringBuilder(n);
		int i = 0;
		while (i < n) {
			char c = s.charAt(i);
			if (c == '"' || c == '\'') {
				i = consumeString(s, i, out);
			} else if (c == '#') {
				// Line comment: copy verbatim to end of line.
				while (i < n && s.charAt(i) != '\n') {
					out.append(s.charAt(i));
					i++;
				}
			} else if (c == '<') {
				// IRI reference: preserve as structure.
				out.append(c);
				i++;
				while (i < n && s.charAt(i) != '>' && s.charAt(i) != '\n') {
					out.append(s.charAt(i));
					i++;
				}
				if (i < n && s.charAt(i) == '>') {
					out.append('>');
					i++;
				}
			} else if (isNumberStart(s, i, out)) {
				i = consumeNumber(s, i, out);
			} else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	private static int consumeString(String s, int start, StringBuilder out) {
		int n = s.length();
		char quote = s.charAt(start);
		boolean triple = start + 2 < n && s.charAt(start + 1) == quote && s.charAt(start + 2) == quote;
		int i = start + (triple ? 3 : 1);
		while (i < n) {
			char c = s.charAt(i);
			if (c == '\\') {
				i += 2; // skip escaped character
				continue;
			}
			if (triple) {
				if (c == quote && i + 2 < n && s.charAt(i + 1) == quote && s.charAt(i + 2) == quote) {
					i += 3;
					break;
				}
				i++;
			} else {
				if (c == quote) {
					i++;
					break;
				}
				if (c == '\n') {
					break; // unterminated literal; stop defensively
				}
				i++;
			}
		}
		out.append('?');
		return i;
	}

	private static boolean isNumberStart(String s, int i, StringBuilder out) {
		char c = s.charAt(i);
		boolean startsDigit = Character.isDigit(c);
		boolean signedOrDotDigit = (c == '+' || c == '-' || c == '.') && i + 1 < s.length()
				&& Character.isDigit(s.charAt(i + 1));
		if (!startsDigit && !signedOrDotDigit) {
			return false;
		}
		// Only treat as a numeric literal at a token boundary, so digits inside variables (?x1), prefixed names
		// (ex:p1) or decimals already being consumed are left untouched.
		if (out.length() == 0) {
			return true;
		}
		char last = out.charAt(out.length() - 1);
		return !(Character.isLetterOrDigit(last) || last == '_' || last == '?' || last == '$' || last == ':'
				|| last == '.');
	}

	private static int consumeNumber(String s, int start, StringBuilder out) {
		int n = s.length();
		int i = start;
		if (s.charAt(i) == '+' || s.charAt(i) == '-') {
			i++;
		}
		while (i < n && Character.isDigit(s.charAt(i))) {
			i++;
		}
		if (i < n && s.charAt(i) == '.') {
			i++;
			while (i < n && Character.isDigit(s.charAt(i))) {
				i++;
			}
		}
		if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
			int j = i + 1;
			if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
				j++;
			}
			if (j < n && Character.isDigit(s.charAt(j))) {
				i = j;
				while (i < n && Character.isDigit(s.charAt(i))) {
					i++;
				}
			}
		}
		out.append('?');
		return i;
	}
}
