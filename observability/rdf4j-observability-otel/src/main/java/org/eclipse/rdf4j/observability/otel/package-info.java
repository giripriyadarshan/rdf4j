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

/**
 * Optional OpenTelemetry instrumentation for RDF4J.
 * <p>
 * The public entry points are {@link org.eclipse.rdf4j.observability.otel.Rdf4jTelemetry} and its builder
 * {@link org.eclipse.rdf4j.observability.otel.Rdf4jTelemetryBuilder}. Everything else in this package is an internal
 * implementation detail and not part of the public API.
 * <p>
 * The instrumentation depends only on the stable {@code opentelemetry-api} (never the SDK), is disabled unless a
 * repository/connection is explicitly wrapped, and follows the OpenTelemetry database semantic conventions.
 */
package org.eclipse.rdf4j.observability.otel;
