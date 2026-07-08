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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.MDC;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter creating OpenTelemetry SERVER spans for incoming requests to the RDF4J HTTP server, continuing the
 * caller's distributed trace via W3C {@code traceparent} extraction. The repository id is reported as
 * {@code db.namespace} and route templates keep span names low-cardinality (e.g. {@code GET
 * /repositories/{repositoryId}/statements}). Trace and span ids are bridged into the SLF4J {@code MDC}
 * ({@code trace_id}/{@code span_id}) for log correlation.
 * <p>
 * Inactive unless {@code otel.instrumentation.rdf4j.enabled=true} (or the corresponding environment variable) is set;
 * the server layer can be individually disabled via {@code otel.instrumentation.rdf4j-server.enabled=false}. Spans are
 * reported to {@link GlobalOpenTelemetry}, i.e. the OpenTelemetry Java agent's SDK when the agent is attached.
 * <p>
 * When another instrumentation (typically the OpenTelemetry Java agent's servlet instrumentation) has already created a
 * local SERVER span for the request, this filter does not create a duplicate: it only enriches the existing span with
 * the RDF4J-specific attributes. Synchronous request processing is assumed, matching the RDF4J server controllers.
 * <p>
 * This class requires the Jakarta Servlet API on the classpath (a {@code provided} dependency of this module); it is
 * only loaded when actually registered in a servlet container, so applications using only the client/engine
 * instrumentation are unaffected.
 */
public final class Rdf4jServerTracingFilter implements Filter {

	private static final String TRACE_ID_MDC_KEY = "trace_id";
	private static final String SPAN_ID_MDC_KEY = "span_id";

	private static final TextMapGetter<HttpServletRequest> GETTER = new TextMapGetter<>() {

		@Override
		public Iterable<String> keys(HttpServletRequest carrier) {
			List<String> keys = new ArrayList<>();
			Enumeration<String> names = carrier.getHeaderNames();
			if (names != null) {
				while (names.hasMoreElements()) {
					keys.add(names.nextElement());
				}
			}
			return keys;
		}

		@Override
		public String get(HttpServletRequest carrier, String key) {
			return carrier != null ? carrier.getHeader(key) : null;
		}
	};

	private volatile Tracer tracer;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (!RuntimeConfiguration.isServerInstrumentationEnabled() || !(request instanceof HttpServletRequest)
				|| !(response instanceof HttpServletResponse)) {
			chain.doFilter(request, response);
			return;
		}

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		String path = pathWithinApplication(httpRequest);
		String repositoryId = repositoryIdOf(path);

		Span active = Span.current();
		if (active.getSpanContext().isValid() && !active.getSpanContext().isRemote()) {
			// another instrumentation (e.g. the OpenTelemetry Java agent) already created a SERVER span
			// for this request: enrich it instead of creating a duplicate
			if (repositoryId != null) {
				active.setAttribute(SemanticConventions.DB_NAMESPACE, repositoryId);
			}
			chain.doFilter(request, response);
			return;
		}

		Context parentContext = GlobalOpenTelemetry.getPropagators()
				.getTextMapPropagator()
				.extract(Context.current(), httpRequest, GETTER);
		String route = templatedRoute(path);
		Span span = tracer().spanBuilder(httpRequest.getMethod() + " " + route)
				.setParent(parentContext)
				.setSpanKind(SpanKind.SERVER)
				.setAttribute(SemanticConventions.HTTP_REQUEST_METHOD, httpRequest.getMethod())
				.setAttribute(SemanticConventions.HTTP_ROUTE, route)
				.setAttribute(SemanticConventions.URL_PATH, path)
				.startSpan();
		if (repositoryId != null) {
			span.setAttribute(SemanticConventions.DB_NAMESPACE, repositoryId);
		}
		try (Scope ignored = span.makeCurrent()) {
			MDC.put(TRACE_ID_MDC_KEY, span.getSpanContext().getTraceId());
			MDC.put(SPAN_ID_MDC_KEY, span.getSpanContext().getSpanId());
			chain.doFilter(request, response);
			int status = httpResponse.getStatus();
			span.setAttribute(SemanticConventions.HTTP_RESPONSE_STATUS_CODE, (long) status);
			if (status >= 500) {
				span.setStatus(StatusCode.ERROR);
			}
		} catch (Throwable t) {
			span.setAttribute(SemanticConventions.ERROR_TYPE, t.getClass().getName());
			span.setStatus(StatusCode.ERROR, t.getMessage());
			span.recordException(t);
			throw t;
		} finally {
			MDC.remove(TRACE_ID_MDC_KEY);
			MDC.remove(SPAN_ID_MDC_KEY);
			span.end();
		}
	}

	private Tracer tracer() {
		Tracer result = tracer;
		if (result == null) {
			synchronized (this) {
				result = tracer;
				if (result == null) {
					// resolved lazily so that an OpenTelemetry agent/SDK has been installed by the time
					// the first instrumented request arrives
					result = GlobalOpenTelemetry.getTracer(SemanticConventions.INSTRUMENTATION_SCOPE_NAME);
					tracer = result;
				}
			}
		}
		return result;
	}

	private static String pathWithinApplication(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (uri == null) {
			return "/";
		}
		if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
			uri = uri.substring(contextPath.length());
		}
		return uri.isEmpty() ? "/" : uri;
	}

	/**
	 * @return the repository id for paths of the form {@code /repositories/<id>[/...]}, otherwise {@code null}
	 */
	private static String repositoryIdOf(String path) {
		List<String> segments = segmentsOf(path);
		if (segments.size() >= 2 && "repositories".equals(segments.get(0))) {
			return segments.get(1);
		}
		return null;
	}

	/**
	 * Produces a low-cardinality route template: the repository id and transaction id path segments are replaced with
	 * placeholders, e.g. {@code /repositories/{repositoryId}/transactions/{transactionId}}.
	 */
	private static String templatedRoute(String path) {
		List<String> segments = segmentsOf(path);
		if (segments.isEmpty()) {
			return "/";
		}
		if ("repositories".equals(segments.get(0)) && segments.size() >= 2) {
			segments.set(1, "{repositoryId}");
		}
		int transactions = segments.indexOf("transactions");
		if (transactions >= 0 && transactions + 1 < segments.size()) {
			segments.set(transactions + 1, "{transactionId}");
		}
		return "/" + String.join("/", segments);
	}

	private static List<String> segmentsOf(String path) {
		if (path == null || path.isEmpty() || "/".equals(path)) {
			return Collections.emptyList();
		}
		List<String> segments = new ArrayList<>();
		for (String segment : path.split("/")) {
			if (!segment.isEmpty()) {
				segments.add(segment);
			}
		}
		return segments;
	}
}
