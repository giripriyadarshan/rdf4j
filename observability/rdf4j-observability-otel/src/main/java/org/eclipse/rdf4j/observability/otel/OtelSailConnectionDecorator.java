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

import java.io.File;

import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.observability.SailConnectionDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;

/**
 * Transparent, zero-code OpenTelemetry instrumentation for all {@code AbstractSail}-based stores, registered through
 * the {@link SailConnectionDecorator} service provider interface. With this artifact on the classpath, setting
 * {@code otel.instrumentation.rdf4j.enabled=true} (or {@code OTEL_INSTRUMENTATION_RDF4J_ENABLED=true}) is all that is
 * needed to obtain engine-level query and transaction spans; spans are reported to {@link GlobalOpenTelemetry}, i.e.
 * the OpenTelemetry Java agent's SDK when the agent is attached.
 * <p>
 * The Sail-level layer can be individually disabled via {@code otel.instrumentation.rdf4j-sail.enabled=false} while
 * keeping the explicitly wrapped repository-level instrumentation active.
 * <p>
 * When disabled (the default), connections are returned unchanged.
 */
public final class OtelSailConnectionDecorator implements SailConnectionDecorator {

	private static final Logger logger = LoggerFactory.getLogger(OtelSailConnectionDecorator.class);

	private volatile Rdf4jTelemetry telemetry;

	@Override
	public SailConnection decorate(Sail sail, SailConnection connection) {
		try {
			if (!RuntimeConfiguration.isSailInstrumentationEnabled()) {
				return connection;
			}
			OperationInstrumenter instrumenter = telemetry().instrumenter();
			String namespace = namespaceOf(sail);
			if (connection instanceof NotifyingSailConnection) {
				return new InstrumentedNotifyingSailConnection((NotifyingSailConnection) connection, instrumenter,
						namespace);
			}
			return new InstrumentedSailConnection(connection, instrumenter, namespace);
		} catch (RuntimeException e) {
			// observability must never break store access
			logger.warn("Failed to instrument sail connection: {}", e.getMessage());
			logger.debug("Details: ", e);
			return connection;
		}
	}

	private Rdf4jTelemetry telemetry() {
		Rdf4jTelemetry result = telemetry;
		if (result == null) {
			synchronized (this) {
				result = telemetry;
				if (result == null) {
					// resolved lazily so that an OpenTelemetry agent/SDK has been installed by the time
					// the first instrumented connection is opened
					result = Rdf4jTelemetry.create(GlobalOpenTelemetry.get());
					telemetry = result;
				}
			}
		}
		return result;
	}

	private static String namespaceOf(Sail sail) {
		File dataDir = sail.getDataDir();
		return dataDir != null ? dataDir.getName() : null;
	}
}
