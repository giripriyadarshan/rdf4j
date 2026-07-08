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
 * Reads the ecosystem-standard enable switch used by the configuration-driven entry point
 * {@link Rdf4jTelemetry#fromConfiguration()}. The instrumentation is disabled unless explicitly turned on.
 * <p>
 * Recognized keys (system property takes precedence over environment variable):
 * <ul>
 * <li>{@code otel.instrumentation.rdf4j.enabled} / {@code OTEL_INSTRUMENTATION_RDF4J_ENABLED}</li>
 * </ul>
 */
final class RuntimeConfiguration {

	static final String ENABLED_PROPERTY = "otel.instrumentation.rdf4j.enabled";
	static final String ENABLED_ENV = "OTEL_INSTRUMENTATION_RDF4J_ENABLED";

	static final String SAIL_ENABLED_PROPERTY = "otel.instrumentation.rdf4j-sail.enabled";
	static final String SAIL_ENABLED_ENV = "OTEL_INSTRUMENTATION_RDF4J_SAIL_ENABLED";

	static final String CLIENT_ENABLED_PROPERTY = "otel.instrumentation.rdf4j-client.enabled";
	static final String CLIENT_ENABLED_ENV = "OTEL_INSTRUMENTATION_RDF4J_CLIENT_ENABLED";

	static final String SERVER_ENABLED_PROPERTY = "otel.instrumentation.rdf4j-server.enabled";
	static final String SERVER_ENABLED_ENV = "OTEL_INSTRUMENTATION_RDF4J_SERVER_ENABLED";

	private RuntimeConfiguration() {
	}

	static boolean isInstrumentationEnabled() {
		return readBoolean(ENABLED_PROPERTY, ENABLED_ENV, false);
	}

	/**
	 * Whether the transparent Sail-level (engine) instrumentation is enabled: requires the master switch, and can be
	 * individually disabled (e.g. when an application already instruments the repository layer and does not want
	 * additional engine-level spans).
	 */
	static boolean isSailInstrumentationEnabled() {
		return isInstrumentationEnabled() && readBoolean(SAIL_ENABLED_PROPERTY, SAIL_ENABLED_ENV, true);
	}

	/**
	 * Whether the transparent remote-client instrumentation (spans and trace-context propagation for operations sent to
	 * remote SPARQL endpoints) is enabled: requires the master switch, and can be individually disabled.
	 */
	static boolean isClientInstrumentationEnabled() {
		return isInstrumentationEnabled() && readBoolean(CLIENT_ENABLED_PROPERTY, CLIENT_ENABLED_ENV, true);
	}

	/**
	 * Whether the server-side instrumentation (SERVER spans for incoming requests to the RDF4J HTTP server) is enabled:
	 * requires the master switch, and can be individually disabled.
	 */
	static boolean isServerInstrumentationEnabled() {
		return isInstrumentationEnabled() && readBoolean(SERVER_ENABLED_PROPERTY, SERVER_ENABLED_ENV, true);
	}

	private static boolean readBoolean(String systemProperty, String environmentVariable, boolean defaultValue) {
		String value = System.getProperty(systemProperty);
		if (value == null || value.isEmpty()) {
			value = System.getenv(environmentVariable);
		}
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value.trim());
	}
}
