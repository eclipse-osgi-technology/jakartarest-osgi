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
package org.eclipse.osgitech.rest.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.osgitech.rest.provider.JerseyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.condition.Condition;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

/**
 * Tests the runtime checker for correct working
 * @author mark
 * @since 11.10.2022
 */
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
public class JerseyRuntimeCheckerTest {

	private BundleContext ctx;
	
	@BeforeEach
	public void before(@InjectBundleContext BundleContext ctx) {
		this.ctx = ctx;
	}
	
	@ParameterizedTest
	@ValueSource(strings = {"org.glassfish.jersey.inject.jersey-hk2", "org.glassfish.hk2.osgi-resource-locator", "org.glassfish.jersey.core.jersey-common", "org.glassfish.jersey.core.jersey-client"})
	public void testBundlesJerseyCondition(String bns, @InjectService(cardinality = 0, filter = "(" + Condition.CONDITION_ID + "=" + JerseyConstants.JERSEY_RUNTIME + ")") ServiceAware<Condition> jerseyCondition) {
		assertNotNull(ctx);
		assertFalse(jerseyCondition.isEmpty());
		ServiceReference<Condition> jerseyConditionRef = jerseyCondition.getServiceReference();
		assertNotNull(jerseyConditionRef.getProperty(JerseyConstants.JERSEY_CLIENT_ONLY));
		assertFalse((Boolean)jerseyConditionRef.getProperty(JerseyConstants.JERSEY_CLIENT_ONLY));
		Bundle injectBundle = getBundle("org.glassfish.jersey.inject.jersey-hk2");
		assertNotNull(injectBundle);
		try {
			injectBundle.stop();
		} catch (BundleException e) {
			fail("Failed to stop inject bundle");
		}
		try {
			Thread.sleep(500l);
		} catch (InterruptedException e) {
			fail("Failed to thread sleep");
			Thread.currentThread().interrupt();
		}
		assertTrue(jerseyCondition.isEmpty());
		try {
			injectBundle.start();
		} catch (BundleException e) {
			fail("Failed to start inject bundle again");
		}
		try {
			Thread.sleep(500l);
		} catch (InterruptedException e) {
			fail("Failed to thread sleep");
			Thread.currentThread().interrupt();
		}
		
	}
	
	/**
	 * @param string
	 */
	private void fail(String string) {
		// TODO Auto-generated method stub
		
	}

	Bundle getBundle(String bsn) {
		for ( Bundle b : ctx.getBundles()) {
			if (b.getSymbolicName().equals(bsn)) {
				return b;
			}
		}
		return null;
	}

}
