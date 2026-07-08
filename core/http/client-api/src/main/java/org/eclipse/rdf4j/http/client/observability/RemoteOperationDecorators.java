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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.eclipse.rdf4j.common.annotation.InternalUseOnly;
import org.eclipse.rdf4j.http.client.observability.RemoteOperationDecorator.RemoteOperationExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers {@link RemoteOperationDecorator} implementations through {@link ServiceLoader} and applies them around
 * remote SPARQL protocol operations. Discovery happens once, on first use; when no decorators are present (the common
 * case), {@link #decorate} reduces to an empty-list check.
 */
@InternalUseOnly
public final class RemoteOperationDecorators {

	private RemoteOperationDecorators() {
	}

	private static class Holder {
		private static final List<RemoteOperationDecorator> DECORATORS = loadDecorators();
	}

	private static List<RemoteOperationDecorator> loadDecorators() {
		Logger logger = LoggerFactory.getLogger(RemoteOperationDecorators.class);
		List<RemoteOperationDecorator> decorators = new ArrayList<>();
		try {
			Iterator<RemoteOperationDecorator> iterator = ServiceLoader
					.load(RemoteOperationDecorator.class, RemoteOperationDecorators.class.getClassLoader())
					.iterator();
			while (iterator.hasNext()) {
				try {
					RemoteOperationDecorator decorator = iterator.next();
					decorators.add(decorator);
					logger.debug("Registered remote operation decorator {}", decorator.getClass().getName());
				} catch (ServiceConfigurationError e) {
					logger.warn("Failed to instantiate remote operation decorator: {}", e.getMessage());
					logger.debug("Details: ", e);
				}
			}
		} catch (ServiceConfigurationError e) {
			logger.warn("Failed to load remote operation decorators: {}", e.getMessage());
			logger.debug("Details: ", e);
		}
		return decorators.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(decorators);
	}

	/**
	 * Runs the given execution through all discovered decorators, in discovery order.
	 *
	 * @param operation description of the operation about to be executed
	 * @param execution the operation itself
	 * @return the operation result
	 * @throws IOException if the execution throws it
	 */
	public static <T> T decorate(RemoteOperation operation, RemoteOperationExecution<T> execution) throws IOException {
		List<RemoteOperationDecorator> decorators = Holder.DECORATORS;
		if (decorators.isEmpty()) {
			return execution.execute();
		}
		return decorate(decorators, 0, operation, execution);
	}

	private static <T> T decorate(List<RemoteOperationDecorator> decorators, int index, RemoteOperation operation,
			RemoteOperationExecution<T> execution) throws IOException {
		if (index >= decorators.size()) {
			return execution.execute();
		}
		return decorators.get(index)
				.decorate(operation, () -> decorate(decorators, index + 1, operation, execution));
	}
}
