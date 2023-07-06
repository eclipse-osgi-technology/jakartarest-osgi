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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.osgi.util.tracker.ServiceTracker;

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
public class ResourceDTOTests {

	private BundleContext ctx;
	
	private ServiceTracker<JakartarsServiceRuntime, Semaphore> tracker;
	
	private Hashtable<String, Boolean> resourceProps() {
		return new Hashtable<>(Map.of("osgi.jakartars.resource", true));
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
	public void testResourceWithoutMethods() throws Exception {
		
		Semaphore semaphore = tracker.waitForService(5000);
		assertNotNull(semaphore);
		
		while(semaphore.tryAcquire(200, TimeUnit.MILLISECONDS));
		
		ServiceRegistration<Object> registration = ctx.registerService(Object.class, new Object(), resourceProps());
		
		assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
		
		ResourceDTO[] resourceDTOs = getRuntime().getRuntimeDTO().defaultApplication.resourceDTOs;
		
		assertNotNull(resourceDTOs);
		assertEquals(1, resourceDTOs.length);
		assertEquals(registration.getReference().getProperty("service.id"), resourceDTOs[0].serviceId);
		assertNotNull(resourceDTOs[0].resourceMethods);
		assertEquals(0, resourceDTOs[0].resourceMethods.length);
	}

}
