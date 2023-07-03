/**
 * Copyright (c) 2023 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Tim Ward - initial API and implementation
 */
package org.eclipse.osgitech.rest.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.osgi.util.tracker.ServiceTracker;

import jakarta.annotation.Priority;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

/**
 * Tests the runtime checker for correct working
 * @author mark
 * @since 11.10.2022
 */
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
@WithConfiguration( pid = "JakartarsServletWhiteboardRuntimeComponent", location = "?",
    properties = { 
        @Property(key = "osgi.http.whiteboard.target", value = "(osgi.http.endpoint=*)") 
    }
)
public class ExtensionPriorityTests {

	private BundleContext ctx;
	
	private ServiceTracker<JakartarsServiceRuntime, Semaphore> tracker;
	
	private Hashtable<String, Object> resourceProps() {
		return new Hashtable<>(Map.of("osgi.jakartars.resource", true));
	}

	private Hashtable<String, Object> extensionProps(Long ranking) {
		return new Hashtable<>(Map.of("osgi.jakartars.extension", true, "service.ranking", ranking));
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
	public void testPriorityAnnotation(@InjectService ClientBuilder cb) throws Exception {
		
		Semaphore semaphore = tracker.waitForService(5000);
		assertNotNull(semaphore);
		semaphore.drainPermits();
		
		ctx.registerService(ContainerResponseFilter.class, 
				new LowPrioritySwapper("Bam", "Boom"), extensionProps(1L));
		
		ctx.registerService(ContainerResponseFilter.class, 
				new HighPrioritySwapper("BamBam", "WhamBam"), extensionProps(-1L));

		ctx.registerService(Object.class, new BamBamResource(), resourceProps());
		
		assertTrue(semaphore.tryAcquire(8, 5, TimeUnit.SECONDS));
		
		assertEquals("WhamBoom", cb.build().target("http://localhost:8080/bam")
			.request().get(String.class));
	}

	@Path("bam")
	public static class BamBamResource {
		
		@GET
		public String getValue() {
			return "BamBam";
		}
	}
	
	private static abstract class BaseSwapper implements ContainerResponseFilter {
		
		private final String target;
		private final String replacement;
		
		public BaseSwapper(String target, String replacement) {
			super();
			this.target = target;
			this.replacement = replacement;
		}

		@Override
		public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
				throws IOException {
			String entity = (String) responseContext.getEntity();
			responseContext.setEntity(entity.replace(target, replacement));
		}
	}
	
	// Priorities are reversed for response filters
	@Priority(10000)
	public static class HighPrioritySwapper extends BaseSwapper {
		public HighPrioritySwapper(String target, String replacement) {
			super(target, replacement);
		}
	}

	// Priorities are reversed for response filters
	@Priority(1000)
	public static class LowPrioritySwapper extends BaseSwapper {
		public LowPrioritySwapper(String target, String replacement) {
			super(target, replacement);
		}
	}
}
