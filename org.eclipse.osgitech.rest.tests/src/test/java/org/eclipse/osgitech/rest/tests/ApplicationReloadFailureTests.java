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

import static org.eclipse.osgitech.rest.tests.RuntimeDTOs.defaultApplicationHasResource;
import static org.eclipse.osgitech.rest.tests.RuntimeDTOs.serviceId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

/**
 * Regression test for issue #70: when the application cannot be rebuilt during a reload, e.g.
 * because an extension throws while it is configured, Jersey has already shut the previous
 * application down. The runtime must report the application as failed instead of as healthy,
 * and the application must come back as soon as the cause is gone.
 */
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
@WithConfiguration( pid = "JakartarsServletWhiteboardRuntimeComponent", location = "?",
    properties = { 
        @Property(key = "osgi.http.whiteboard.target", value = "(osgi.http.endpoint=*)") 
    }
)
public class ApplicationReloadFailureTests {

	private static final String EXTENSION_NAME = "BrokenFeature";

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private Hashtable<String, Object> resourceProps() {
		return new Hashtable<>(Map.of(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, true));
	}

	private Hashtable<String, Object> extensionProps() {
		return new Hashtable<>(Map.of(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, true, 
				JakartarsWhiteboardConstants.JAKARTA_RS_NAME, EXTENSION_NAME));
	}
	
	@Test
	public void testFailedReloadIsReportedAndRecovers(@InjectBundleContext BundleContext ctx,
			@InjectService(timeout = 10000) JakartarsServiceRuntime runtime,
			@InjectService ClientBuilder cb) throws Exception {
		
		ServiceRegistration<?> resourceReg = ctx.registerService(Object.class, new ScopesResource(), resourceProps());
		long resourceServiceId = serviceId(resourceReg);
		
		RuntimeDTOs.await(runtime, TIMEOUT, dto -> defaultApplicationHasResource(dto, resourceServiceId));
		
		String target = "http://localhost:8080/scopes";
		assertEquals("scopes", cb.build().target(target).request().get(String.class));
		
		// A feature that fails while the application is configured takes the whole reload down
		ServiceRegistration<?> extensionReg = ctx.registerService(Feature.class, new BrokenFeature(), extensionProps());
		
		RuntimeDTO runtimeDTO = RuntimeDTOs.await(runtime, TIMEOUT, dto -> failedApplication(dto).isPresent());
		
		assertNull(runtimeDTO.defaultApplication, "The default application is still reported as healthy");
		assertEquals(DTOConstants.FAILURE_REASON_UNKNOWN, failedApplication(runtimeDTO).get().failureReason);
		
		// As soon as the cause is gone the application has to be served again
		extensionReg.unregister();
		
		runtimeDTO = RuntimeDTOs.await(runtime, TIMEOUT, dto -> defaultApplicationHasResource(dto, resourceServiceId));
		
		assertTrue(failedApplication(runtimeDTO).isEmpty(), "The default application is still reported as failed");
		assertEquals("scopes", cb.build().target(target).request().get(String.class),
				"The application did not recover after the failing extension was removed");
	}

	private Optional<FailedApplicationDTO> failedApplication(RuntimeDTO dto) {
		return dto.failedApplicationDTOs == null ? Optional.empty() : Arrays.stream(dto.failedApplicationDTOs)
				.filter(f -> JakartarsWhiteboardConstants.JAKARTA_RS_DEFAULT_APPLICATION.equals(f.name))
				.findFirst();
	}

	@Path("scopes")
	public static class ScopesResource {
		
		@GET
		public String getValue() {
			return "scopes";
		}
	}
	
	/**
	 * A feature extension that fails while the application is configured.
	 */
	public static class BrokenFeature implements Feature {

		@Override
		public boolean configure(FeatureContext context) {
			throw new IllegalArgumentException("This feature cannot be configured");
		}
	}
}
