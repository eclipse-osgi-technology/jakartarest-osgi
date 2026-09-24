/**
 * Copyright (c) 2026 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.eclipse.osgitech.rest.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.FailedExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.osgi.util.tracker.ServiceTracker;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * Regression test for issue #60: a singleton extension with an injected field that Jersey
 * cannot satisfy while the application is configured (a non proxiable request scoped
 * <code>@Context</code> like {@link ContainerRequestContext}) must not take the whole
 * application down. The extension has to be reported as failed and the other content
 * has to keep serving.
 */
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
@WithConfiguration( pid = "JakartarsServletWhiteboardRuntimeComponent", location = "?",
    properties = { 
        @Property(key = "osgi.http.whiteboard.target", value = "(osgi.http.endpoint=*)") 
    }
)
public class ExtensionInjectionFailureTests {

	private static final String EXTENSION_NAME = "RequestContextMapper";

	private BundleContext ctx;
	
	private ServiceTracker<JakartarsServiceRuntime, Semaphore> tracker;
	
	private Hashtable<String, Object> resourceProps() {
		return new Hashtable<>(Map.of("osgi.jakartars.resource", true));
	}

	private Hashtable<String, Object> extensionProps() {
		return new Hashtable<>(Map.of("osgi.jakartars.extension", true, "osgi.jakartars.name", EXTENSION_NAME));
	}
	
	private JakartarsServiceRuntime getRuntime() {
		return ctx.getService(tracker.getServiceReference());
	}

	@BeforeEach
	public void before(@InjectBundleContext BundleContext ctx) {
		this.ctx = ctx;
		
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
	}
	
	@Test
	public void testExtensionWithRequestScopedContextFieldIsFailedNotFatal(@InjectService ClientBuilder cb) throws Exception {
		
		Semaphore semaphore = tracker.waitForService(5000);
		assertNotNull(semaphore);
		
		while(semaphore.tryAcquire(200, TimeUnit.MILLISECONDS));
		
		ctx.registerService(Object.class, new ScopesResource(), resourceProps());
		
		assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
		
		String target = "http://localhost:8080/scopes";
		assertEquals("scopes", cb.build().target(target).request().get(String.class));
		
		while(semaphore.tryAcquire(200, TimeUnit.MILLISECONDS));
		
		ServiceRegistration<?> extensionReg = ctx.registerService(ExceptionMapper.class.getName(), 
				new RequestContextMapper(), extensionProps());
		
		assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
		
		// The application must still be served by Jersey
		Response response = cb.build().target(target).request().get();
		assertEquals(200, response.getStatus(), "The application went down after registering the extension");
		assertEquals("scopes", response.readEntity(String.class));
		
		long extensionServiceId = (Long) extensionReg.getReference().getProperty("service.id");
		
		RuntimeDTO runtimeDTO = getRuntime().getRuntimeDTO();
		
		// The extension must be reported as failed ...
		assertNotNull(runtimeDTO.failedExtensionDTOs);
		Optional<FailedExtensionDTO> failed = Arrays.stream(runtimeDTO.failedExtensionDTOs)
				.filter(f -> f.serviceId == extensionServiceId)
				.findFirst();
		assertTrue(failed.isPresent(), "The extension is not reported as a failed extension");
		assertEquals(EXTENSION_NAME, failed.get().name);
		assertEquals(DTOConstants.FAILURE_REASON_UNKNOWN, failed.get().failureReason);
		
		// ... and not as a healthy part of the application
		assertNotNull(runtimeDTO.defaultApplication);
		assertNotNull(runtimeDTO.defaultApplication.extensionDTOs);
		assertTrue(Arrays.stream(runtimeDTO.defaultApplication.extensionDTOs)
				.noneMatch(e -> e.serviceId == extensionServiceId), 
				"The failed extension is still listed in the application DTO");
	}

	@Path("scopes")
	public static class ScopesResource {
		
		@GET
		public String getValue() {
			return "scopes";
		}
	}
	
	/**
	 * A singleton extension relying on a request scoped, non proxiable injection target.
	 * Per 151.5.5 this is not supported and may fail, but the failure must be contained.
	 */
	public static class RequestContextMapper implements ExceptionMapper<IllegalStateException> {

		@Context
		ContainerRequestContext requestContext;

		@Override
		public Response toResponse(IllegalStateException exception) {
			return Response.status(409).build();
		}
	}
}
