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
package org.eclipse.osgitech.rest.servlet.whiteboard.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_DEFAULT_CONTEXT_NAME;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.config.InjectConfiguration;
import org.osgi.test.common.annotation.config.WithFactoryConfiguration;
import org.osgi.test.junit5.cm.ConfigurationExtension;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.osgi.util.tracker.ServiceTracker;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

/**
 * Test running whiteboard implementation for the Servlet Whiteboard specification
 * @author Mark Hoffmann
 * @since 09.11.2022
 */
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
@ExtendWith(ConfigurationExtension.class)
public class ServletWhiteboardTest {
	
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
		this.tracker = new ServiceTracker<>(ctx, JakartarsServiceRuntime.class, null) {

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
		while(semaphore.tryAcquire(500, TimeUnit.MILLISECONDS));
	}
	
	@AfterEach
	public void after() {
		this.tracker.close();
	}
	
	@Test
	public void testWhiteboard(@InjectBundleContext BundleContext ctx) throws Exception {
		
		Semaphore semaphore = tracker.waitForService(5000);
		assertNotNull(semaphore);
		semaphore.drainPermits();
		
		Dictionary<String,Object> properties = new Hashtable<>();
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, Boolean.TRUE);

		ctx.registerService(WhiteboardResource.class, new WhiteboardResource(), properties);

		assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));

		String baseURI = getBaseURI(tracker.getServiceReference());
		
		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(baseURI + "whiteboard/resource"))
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertEquals("Hello World", response.body());
	}

	@Test
	public void testWhiteboardExtension(@InjectBundleContext BundleContext ctx) throws Exception {
		
		Semaphore semaphore = tracker.waitForService(5000);
		assertNotNull(semaphore);
		semaphore.drainPermits();
		
		Dictionary<String,Object> properties = new Hashtable<>();
		properties.put(JAKARTA_RS_RESOURCE, Boolean.TRUE);
		
		ctx.registerService(WhiteboardResource.class, new WhiteboardResource(), properties);
		
		properties.remove(JAKARTA_RS_RESOURCE);
		properties.put(JAKARTA_RS_EXTENSION, true);
		ctx.registerService(ContainerResponseFilter.class, new ContainerResponseFilter() {

			@Override
			public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
					throws IOException {
				if(responseContext.getEntityClass() == String.class) {
					String entity = (String) responseContext.getEntity();
					responseContext.setEntity(entity.replace("World", "Universe"));
				}
			}
			
		}, properties);
		
		
		assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
		
		String baseURI = getBaseURI(tracker.getServiceReference());
		
		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(baseURI + "whiteboard/resource"))
				.build();
		
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertEquals("Hello Universe", response.body());
	}
	
	@Test
	public void testServletWhiteboardDefaultContext(@InjectBundleContext BundleContext ctx,
			@InjectConfiguration(withFactoryConfig = 
				@WithFactoryConfiguration(
						factoryPid = "JakartarsServletWhiteboardRuntimeComponent",
						name = "JRSWB")) Configuration config) throws Exception {
		
		Dictionary<String,Object> cfg = config.getProperties();
		Object oldContext = cfg.remove("jersey.context.path");
		config.update(cfg);
		Thread.sleep(500);
		try {
			Semaphore semaphore = tracker.waitForService(5000);
			assertNotNull(semaphore);
			semaphore.drainPermits();
			
			Dictionary<String,Object> properties = new Hashtable<>();
			properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, Boolean.TRUE);
	
			ctx.registerService(WhiteboardResource.class, new WhiteboardResource(), properties);
	
			assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
	
			String baseURI = getBaseURI(tracker.getServiceReference());
			
			HttpRequest request = HttpRequest.newBuilder()
					.GET()
					.uri(URI.create(baseURI + "whiteboard/resource"))
					.build();
	
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode());
			assertEquals("Hello World", response.body());
			
			properties = new Hashtable<>();
			properties.put(HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet");
			properties.put(HTTP_WHITEBOARD_CONTEXT_SELECT, "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=" + HTTP_WHITEBOARD_DEFAULT_CONTEXT_NAME + ")");
			ctx.registerService(Servlet.class, new HttpServlet() {
				/** serialVersionUID */
				private static final long serialVersionUID = 1L;

				@Override
				protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
					resp.getWriter().print("Hello Servlet");
				}
			}, properties);
			
			request = HttpRequest.newBuilder()
					.GET()
					.uri(URI.create(baseURI + "servlet"))
					.build();
	
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode());
			assertEquals("Hello Servlet", response.body());
		} finally {
			cfg.put("jersey.context.path", oldContext);
			config.update(cfg);
		}
	}
	
	protected String getBaseURI(ServiceReference<JakartarsServiceRuntime> runtime) {
		Object value = runtime.getProperty(JAKARTA_RS_SERVICE_ENDPOINT);

		if (value instanceof String) {
			return (String) value;
		} else if (value instanceof String[]) {
			String[] values = (String[]) value;
			if (values.length > 0) {
				return values[values.length -1];
			}
		} else if (value instanceof Collection) {
			if (!((Collection<?>)value).isEmpty()) { 
				return String.valueOf(((Collection< ? >) value).iterator().next());
			}
		}

		throw new IllegalArgumentException(
				"The JAXRS Service Runtime did not declare an endpoint property");
	}

}
