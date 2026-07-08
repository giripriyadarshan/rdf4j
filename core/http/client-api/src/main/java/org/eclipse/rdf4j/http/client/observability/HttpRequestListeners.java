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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.eclipse.rdf4j.common.annotation.InternalUseOnly;
import org.eclipse.rdf4j.http.client.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers {@link HttpRequestListener} implementations through {@link ServiceLoader} and notifies them before
 * outgoing requests are sent. Discovery happens once, on first use; when no listeners are present (the common case),
 * {@link #beforeSend} reduces to an empty-list check.
 */
@InternalUseOnly
public final class HttpRequestListeners {

	private HttpRequestListeners() {
	}

	private static class Holder {
		private static final List<HttpRequestListener> LISTENERS = loadListeners();
	}

	private static List<HttpRequestListener> loadListeners() {
		Logger logger = LoggerFactory.getLogger(HttpRequestListeners.class);
		List<HttpRequestListener> listeners = new ArrayList<>();
		try {
			Iterator<HttpRequestListener> iterator = ServiceLoader
					.load(HttpRequestListener.class, HttpRequestListeners.class.getClassLoader())
					.iterator();
			while (iterator.hasNext()) {
				try {
					HttpRequestListener listener = iterator.next();
					listeners.add(listener);
					logger.debug("Registered http request listener {}", listener.getClass().getName());
				} catch (ServiceConfigurationError e) {
					logger.warn("Failed to instantiate http request listener: {}", e.getMessage());
					logger.debug("Details: ", e);
				}
			}
		} catch (ServiceConfigurationError e) {
			logger.warn("Failed to load http request listeners: {}", e.getMessage());
			logger.debug("Details: ", e);
		}
		return listeners.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(listeners);
	}

	/**
	 * Notifies all discovered listeners that the given request is about to be sent.
	 *
	 * @param request the request about to be sent
	 */
	public static void beforeSend(HttpRequest request) {
		for (HttpRequestListener listener : Holder.LISTENERS) {
			listener.beforeSend(request);
		}
	}
}
