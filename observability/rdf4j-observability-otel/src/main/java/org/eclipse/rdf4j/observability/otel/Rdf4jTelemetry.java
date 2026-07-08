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

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Entry point for OpenTelemetry instrumentation of RDF4J, modeled on the OpenTelemetry JDBC instrumentation: create a
 * {@code Rdf4jTelemetry} from an {@link OpenTelemetry} instance, then use {@link #wrap(Repository)} (or
 * {@link #wrap(RepositoryConnection)}) to obtain an instrumented view that emits spans for SPARQL queries, updates and
 * transaction boundaries following the OpenTelemetry database semantic conventions.
 *
 * <h2>Enabling and disabling</h2>
 * <p>
 * The instrumentation is <strong>disabled by default at every level</strong>:
 * <ul>
 * <li>this module is a separate, opt-in dependency that no default RDF4J artifact pulls in;</li>
 * <li>nothing is instrumented unless a repository/connection is explicitly wrapped; and</li>
 * <li>a telemetry backed by {@link OpenTelemetry#noop()} records nothing at effectively zero cost.</li>
 * </ul>
 *
 * <h2>Typical usage (library / embedded)</h2>
 *
 * <pre>
 * {@code
 * OpenTelemetry openTelemetry = ...;                 // your configured SDK, or GlobalOpenTelemetry.get()
 * Rdf4jTelemetry telemetry = Rdf4jTelemetry.create(openTelemetry);
 * Repository repository = telemetry.wrap(new SPARQLRepository(endpoint), "my-repo");
 * }
 * </pre>
 *
 * <h2>Configuration-driven usage (adopt an ambient agent/SDK)</h2>
 * <p>
 * {@link #fromConfiguration()} returns an enabled telemetry backed by {@link GlobalOpenTelemetry} only when
 * {@code otel.instrumentation.rdf4j.enabled} (system property) or {@code OTEL_INSTRUMENTATION_RDF4J_ENABLED}
 * (environment variable) is set; otherwise it returns a no-op instance. This lets a deployment already running the
 * OpenTelemetry Java agent turn RDF4J spans on with a single flag and no code change.
 *
 * <p>
 * Instances are immutable and thread-safe.
 */
public final class Rdf4jTelemetry {

	private final OperationInstrumenter instrumenter;
	private final boolean enabled;

	Rdf4jTelemetry(OperationInstrumenter instrumenter, boolean enabled) {
		this.instrumenter = instrumenter;
		this.enabled = enabled;
	}

	/**
	 * @return {@code true} when this telemetry was created from an explicitly supplied {@link OpenTelemetry} instance,
	 *         {@code false} for a default/no-op instance. Callers that want to avoid any wrapping when telemetry is off
	 *         can use this to guard their {@code wrap(...)} calls, keeping the unwrapped object graph fully intact
	 *         (e.g. for code that relies on {@code instanceof} checks against concrete repository types).
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Creates a telemetry that reports to the given {@link OpenTelemetry} instance, using default options.
	 */
	public static Rdf4jTelemetry create(OpenTelemetry openTelemetry) {
		return builder().setOpenTelemetry(openTelemetry).build();
	}

	/**
	 * @return a builder for fine-grained configuration.
	 */
	public static Rdf4jTelemetryBuilder builder() {
		return new Rdf4jTelemetryBuilder();
	}

	/**
	 * @return a telemetry backed by {@link OpenTelemetry#noop()} that records nothing.
	 */
	public static Rdf4jTelemetry noop() {
		return builder().build();
	}

	/**
	 * @return an enabled telemetry backed by {@link GlobalOpenTelemetry} when the RDF4J instrumentation enable flag is
	 *         set, otherwise a {@link #noop()} instance. See {@link RuntimeConfiguration}.
	 */
	public static Rdf4jTelemetry fromConfiguration() {
		if (RuntimeConfiguration.isInstrumentationEnabled()) {
			return create(GlobalOpenTelemetry.get());
		}
		return noop();
	}

	/**
	 * Wraps a repository so every connection obtained from it is instrumented.
	 */
	public Repository wrap(Repository repository) {
		return wrap(repository, null);
	}

	/**
	 * Wraps a repository, tagging its spans with the given target namespace (used for {@code db.namespace} and as part
	 * of the span name).
	 *
	 * @param namespace repository id or endpoint identifier, or {@code null} if unknown
	 */
	public Repository wrap(Repository repository, String namespace) {
		return new InstrumentedRepository(repository, this, namespace);
	}

	/**
	 * Wraps a single connection.
	 */
	public RepositoryConnection wrap(RepositoryConnection connection) {
		return wrap(connection, null);
	}

	/**
	 * Wraps a single connection, tagging its spans with the given target namespace.
	 *
	 * @param namespace repository id or endpoint identifier, or {@code null} if unknown
	 */
	public RepositoryConnection wrap(RepositoryConnection connection, String namespace) {
		return new InstrumentedRepositoryConnection(connection.getRepository(), connection, this, namespace);
	}

	OperationInstrumenter instrumenter() {
		return instrumenter;
	}
}
