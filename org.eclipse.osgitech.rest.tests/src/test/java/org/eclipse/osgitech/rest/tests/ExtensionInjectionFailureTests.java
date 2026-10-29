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

import static org.eclipse.osgitech.rest.tests.RuntimeDTOs.defaultApplicationHasExtension;
import static org.eclipse.osgitech.rest.tests.RuntimeDTOs.defaultApplicationHasResource;
import static org.eclipse.osgitech.rest.tests.RuntimeDTOs.hasFailedExtension;
import static org.eclipse.osgitech.rest.tests.RuntimeDTOs.serviceId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
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

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private Hashtable<String, Object> resourceProps() {
		return new Hashtable<>(Map.of("osgi.jakartars.resource", true));
	}

	private Hashtable<String, Object> extensionProps() {
		return new Hashtable<>(Map.of("osgi.jakartars.extension", true, "osgi.jakartars.name", EXTENSION_NAME));
	}
	
	@Test
	public void testExtensionWithRequestScopedContextFieldIsFailedNotFatal(@InjectBundleContext BundleContext ctx,
			@InjectService(timeout = 10000) JakartarsServiceRuntime runtime,
			@InjectService ClientBuilder cb) throws Exception {
		
		ServiceRegistration<?> resourceReg = ctx.registerService(Object.class, new ScopesResource(), resourceProps());
		long resourceServiceId = serviceId(resourceReg);
		
		RuntimeDTOs.await(runtime, TIMEOUT, dto -> defaultApplicationHasResource(dto, resourceServiceId));
		
		String target = "http://localhost:8080/scopes";
		assertEquals("scopes", cb.build().target(target).request().get(String.class));
		
		ServiceRegistration<?> extensionReg = ctx.registerService(ExceptionMapper.class.getName(), 
				new RequestContextMapper(), extensionProps());
		long extensionServiceId = serviceId(extensionReg);
		
		// The extension must be reported as failed ...
		RuntimeDTO runtimeDTO = RuntimeDTOs.await(runtime, TIMEOUT, dto -> hasFailedExtension(dto, extensionServiceId));
		
		Optional<FailedExtensionDTO> failed = Arrays.stream(runtimeDTO.failedExtensionDTOs)
				.filter(f -> f.serviceId == extensionServiceId)
				.findFirst();
		assertTrue(failed.isPresent());
		assertEquals(EXTENSION_NAME, failed.get().name);
		assertEquals(DTOConstants.FAILURE_REASON_UNKNOWN, failed.get().failureReason);
		
		// ... and not as a healthy part of the application
		assertFalse(defaultApplicationHasExtension(runtimeDTO, extensionServiceId),
				"The failed extension is still listed in the application DTO");
		assertTrue(defaultApplicationHasResource(runtimeDTO, resourceServiceId));
		
		// The application must still be served by Jersey
		Response response = cb.build().target(target).request().get();
		assertEquals(200, response.getStatus(), "The application went down after registering the extension");
		assertEquals("scopes", response.readEntity(String.class));
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
