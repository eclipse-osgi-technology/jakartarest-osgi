/**
 * Copyright (c) 2012 - 2022 Data In Motion and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.eclipse.osgitech.rest.tests.subresource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Integration test verifying that prototype-scoped sub-resources obtained via
 * ComponentServiceObjects are properly cleaned up (ungetService called) after
 * a request completes.
 *
 * Test scenario:
 * 1. ParentResource is a prototype-scoped DS component registered as a JAX-RS resource
 * 2. ParentResource has a sub-resource locator that returns SubResource instances
 * 3. SubResource is obtained via ComponentServiceObjects (SCR-managed)
 * 4. After request completion, both resources should be deactivated in correct order
 */
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
public class SubResourceTest {

	private ServiceTracker<JakartarsServiceRuntime, Semaphore> tracker;
	private static HttpClient httpClient;

	@BeforeAll
	public static void setupHttpClient() {
		httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	@BeforeEach
	public void before(@InjectBundleContext BundleContext ctx) throws InterruptedException {
		tracker = new ServiceTracker<>(ctx, JakartarsServiceRuntime.class, null) {

			@Override
			public Semaphore addingService(ServiceReference<JakartarsServiceRuntime> reference) {
				return new Semaphore(1);
			}

			@Override
			public void modifiedService(ServiceReference<JakartarsServiceRuntime> reference, Semaphore service) {
				service.release();
			}

			@Override
			public void removedService(ServiceReference<JakartarsServiceRuntime> reference, Semaphore service) {
				service.release();
			}
		};

		tracker.open();

		Semaphore semaphore = tracker.waitForService(5000);
		assertNotNull(semaphore);
		// Wait for the whiteboard to be in a steady state
		while (semaphore.tryAcquire(500, TimeUnit.MILLISECONDS));
	}

	@AfterEach
	public void after() {
		tracker.close();
	}

	@Test
	public void testSubResourcePrototypeCleanup(
			@InjectService(cardinality = 1, timeout = 5000) LifecycleTracker lifecycleTracker) throws Exception {

		Semaphore semaphore = tracker.waitForService(5000);
		assertNotNull(semaphore);

		// Clear any previous events
		lifecycleTracker.clear();

		// Wait a bit for the whiteboard to stabilize with our components
		semaphore.drainPermits();
		Thread.sleep(500);

		String baseURI = getBaseURI(tracker.getServiceReference());

		// Make request to sub-resource: /parent/test/sub/data
		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(baseURI + "parent/test/sub/data"))
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertEquals("sub-resource-data", response.body());

		// Give some time for cleanup to happen
		Thread.sleep(500);

		// Verify lifecycle events
		List<String> events = lifecycleTracker.getEvents();

		// Expected order:
		// 1. ACTIVATE:ParentResource (whiteboard gets parent service)
		// 2. ACTIVATE:SubResource (parent's locator gets sub-resource)
		// 3. DEACTIVATE:SubResource (SCR releases sub-resource)
		// 4. DEACTIVATE:ParentResource (whiteboard releases parent)
		assertTrue(events.contains("ACTIVATE:ParentResource"),
				"ParentResource should be activated. Events: " + events);
		assertTrue(events.contains("ACTIVATE:SubResource"),
				"SubResource should be activated. Events: " + events);
		assertTrue(events.contains("DEACTIVATE:SubResource"),
				"SubResource should be deactivated (ungetService called). Events: " + events);
		assertTrue(events.contains("DEACTIVATE:ParentResource"),
				"ParentResource should be deactivated (ungetService called). Events: " + events);

		// Verify order: SubResource should be deactivated before ParentResource
		int subDeactivateIndex = events.indexOf("DEACTIVATE:SubResource");
		int parentDeactivateIndex = events.indexOf("DEACTIVATE:ParentResource");
		assertTrue(subDeactivateIndex < parentDeactivateIndex,
				"SubResource should be deactivated before ParentResource. Events: " + events);
	}

	private String getBaseURI(ServiceReference<JakartarsServiceRuntime> runtime) {
		Object value = runtime.getProperty(JAKARTA_RS_SERVICE_ENDPOINT);

		if (value instanceof String) {
			return (String) value;
		} else if (value instanceof String[]) {
			String[] values = (String[]) value;
			if (values.length > 0) {
				return values[values.length - 1];
			}
		} else if (value instanceof Collection) {
			if (!((Collection<?>) value).isEmpty()) {
				return String.valueOf(((Collection<?>) value).iterator().next());
			}
		}

		throw new IllegalArgumentException(
				"The Jakarta RS Service Runtime did not declare an endpoint property");
	}
}
