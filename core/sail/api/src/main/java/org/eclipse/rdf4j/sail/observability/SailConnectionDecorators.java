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
package org.eclipse.rdf4j.sail.observability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.eclipse.rdf4j.common.annotation.InternalUseOnly;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers {@link SailConnectionDecorator} implementations through {@link ServiceLoader} and applies them to
 * connections handed out by {@code AbstractSail}. Discovery happens once, on first use; when no decorators are present
 * (the common case), {@link #decorate} reduces to an empty-list check.
 */
@InternalUseOnly
public final class SailConnectionDecorators {

	private SailConnectionDecorators() {
	}

	private static class Holder {
		private static final List<SailConnectionDecorator> DECORATORS = loadDecorators();
	}

	private static List<SailConnectionDecorator> loadDecorators() {
		Logger logger = LoggerFactory.getLogger(SailConnectionDecorators.class);
		List<SailConnectionDecorator> decorators = new ArrayList<>();
		try {
			// use this class' own class loader: the decorator implementations are expected to live alongside
			// rdf4j on the same class path (thread-context class loaders are unreliable for background threads)
			Iterator<SailConnectionDecorator> iterator = ServiceLoader
					.load(SailConnectionDecorator.class, SailConnectionDecorators.class.getClassLoader())
					.iterator();
			while (iterator.hasNext()) {
				try {
					SailConnectionDecorator decorator = iterator.next();
					decorators.add(decorator);
					logger.debug("Registered sail connection decorator {}", decorator.getClass().getName());
				} catch (ServiceConfigurationError e) {
					logger.warn("Failed to instantiate sail connection decorator: {}", e.getMessage());
					logger.debug("Details: ", e);
				}
			}
		} catch (ServiceConfigurationError e) {
			logger.warn("Failed to load sail connection decorators: {}", e.getMessage());
			logger.debug("Details: ", e);
		}
		return decorators.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(decorators);
	}

	/**
	 * Applies all discovered decorators to the given connection, in discovery order.
	 *
	 * @param sail       the sail that created the connection
	 * @param connection the connection to decorate
	 * @return the decorated connection, or the supplied connection unchanged when no decorators are registered
	 */
	public static SailConnection decorate(Sail sail, SailConnection connection) {
		List<SailConnectionDecorator> decorators = Holder.DECORATORS;
		if (decorators.isEmpty()) {
			return connection;
		}
		for (SailConnectionDecorator decorator : decorators) {
			connection = decorator.decorate(sail, connection);
		}
		return connection;
	}
}
