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
 *     Stefan Bishof - API and implementation
 *     Tim Ward - implementation
 */
package org.eclipse.osgitech.rest;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.ws.rs.core.Application;

import org.eclipse.osgitech.rest.helper.internal.DispatcherHelper;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationProvider;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Constants;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * 
 * @author Mark Hoffmann
 * @since 21.10.2017
 */
public class DispatcherHelperTest {
	
	/**
	 * Test method for {@link org.eclipse.osgitech.rest.helper.JerseyHelper#isEmpty(jakarta.ws.rs.core.Application)}.
	 */
	@Test
	public void testGetDefaultApplicationIsEmpty() {
		assertNotNull(DispatcherHelper.getDefaultApplications(null));
		assertEquals(0, DispatcherHelper.getDefaultApplications(null).size());
		
		assertNotNull(DispatcherHelper.getDefaultApplication(null));
		assertFalse(DispatcherHelper.getDefaultApplication(null).isPresent());
	}
	
	/**
	 * Test method for {@link org.eclipse.osgitech.rest.helper.JerseyHelper#isEmpty(jakarta.ws.rs.core.Application)}.
	 */
	@Test
	public void testGetDefaultApplicationNoDefault() {
		assertNotNull(DispatcherHelper.getDefaultApplications(null));
		assertEquals(0, DispatcherHelper.getDefaultApplications(null).size());
		
		List<JerseyApplicationProvider> providers = new LinkedList<JerseyApplicationProvider>();
		providers.add(createApplicationProvider("test", Integer.valueOf(10), Long.valueOf(1)));
		providers.add(createApplicationProvider("test3", Integer.valueOf(20), Long.valueOf(2)));
		providers.add(createApplicationProvider("test54", Integer.valueOf(40), Long.valueOf(3)));
		
		assertEquals(0, DispatcherHelper.getDefaultApplications(providers).size());
		assertNotNull(DispatcherHelper.getDefaultApplication(providers));
		assertFalse(DispatcherHelper.getDefaultApplication(providers).isPresent());
	}
	
	/**
	 * Test method for {@link org.eclipse.osgitech.rest.helper.JerseyHelper#isEmpty(jakarta.ws.rs.core.Application)}.
	 */
	@Test
	public void testGetDefaultApplicationOne() {
		assertNotNull(DispatcherHelper.getDefaultApplications(null));
		assertEquals(0, DispatcherHelper.getDefaultApplications(null).size());
		
		List<JerseyApplicationProvider> providers = new LinkedList<JerseyApplicationProvider>();
		providers.add(createApplicationProvider("test", Integer.valueOf(10), Long.valueOf(1)));
		JerseyApplicationProvider defaultProvider = createApplicationProvider(".default", Integer.valueOf(20), Long.valueOf(2));
		providers.add(defaultProvider);
		providers.add(createApplicationProvider("test54", Integer.valueOf(40), Long.valueOf(3)));
		
		Set<JerseyApplicationProvider> result = DispatcherHelper.getDefaultApplications(providers);
		assertEquals(1, result.size());
		Optional<JerseyApplicationProvider> first = result.stream().findFirst();
		assertTrue(first.isPresent());
		assertEquals(defaultProvider, first.get());
		
		first = DispatcherHelper.getDefaultApplication(providers);
		assertNotNull(first);
		assertTrue(first.isPresent());
		assertEquals(defaultProvider, first.get());
		
	}
	
	/**
	 * Test method for {@link org.eclipse.osgitech.rest.helper.JerseyHelper#isEmpty(jakarta.ws.rs.core.Application)}.
	 */
	@Test
	public void testGetDefaultApplicationMany() {
		assertNotNull(DispatcherHelper.getDefaultApplications(null));
		assertEquals(0, DispatcherHelper.getDefaultApplications(null).size());
		
		List<JerseyApplicationProvider> providers = new LinkedList<JerseyApplicationProvider>();
		providers.add(createApplicationProvider("test", Integer.valueOf(10), Long.valueOf(1)));
		JerseyApplicationProvider defaultProvider01 = createApplicationProvider(".default", Integer.valueOf(20), Long.valueOf(2));
		providers.add(defaultProvider01);
		JerseyApplicationProvider defaultProvider02 = createApplicationProvider(".default", Integer.valueOf(30), Long.valueOf(3));
		providers.add(defaultProvider02);
		providers.add(createApplicationProvider("test54", Integer.valueOf(40), Long.valueOf(4)));
		
		Set<JerseyApplicationProvider> result = DispatcherHelper.getDefaultApplications(providers);
		assertEquals(2, result.size());
		int cnt = 0;
		for (JerseyApplicationProvider p : result) {
			switch (cnt) {
			case 0:
				assertEquals(defaultProvider02, p);
				break;
			case 1:
				assertEquals(defaultProvider01, p);
				break;
			}
			cnt++;
		}
		
		Optional<JerseyApplicationProvider> first = DispatcherHelper.getDefaultApplication(providers);
		assertNotNull(first);
		assertTrue(first.isPresent());
		assertEquals(defaultProvider02, first.get());
	}
	
	/**
	 * Test method for {@link org.eclipse.osgitech.rest.helper.JerseyHelper#isEmpty(jakarta.ws.rs.core.Application)}.
	 */
	@Test
	public void testGetDefaultApplicationManyWithRealDefault() {
		assertNotNull(DispatcherHelper.getDefaultApplications(null));
		assertEquals(0, DispatcherHelper.getDefaultApplications(null).size());
		
		List<JerseyApplicationProvider> providers = new LinkedList<JerseyApplicationProvider>();
		providers.add(createApplicationProvider("test", Integer.valueOf(10), Long.valueOf(1)));
		JerseyApplicationProvider defaultProvider01 = createApplicationProvider(".default", Integer.valueOf(20), Long.valueOf(2));
		providers.add(defaultProvider01);
		JerseyApplicationProvider defaultProvider02 = createApplicationProvider(".default", Integer.valueOf(30), Long.valueOf(3));
		providers.add(defaultProvider02);
		providers.add(createApplicationProvider("test54", Integer.valueOf(40), Long.valueOf(4)));
		JerseyApplicationProvider defaultProvider03 = createApplicationProvider(".default", Integer.valueOf(30), Long.valueOf(5));
		providers.add(defaultProvider03);
		
		Set<JerseyApplicationProvider> result = DispatcherHelper.getDefaultApplications(providers);
		assertEquals(3, result.size());
		int cnt = 0;
		for (JerseyApplicationProvider p : result) {
			switch (cnt) {
			case 0:
				assertEquals(defaultProvider02, p);
				break;
			case 1:
				assertEquals(defaultProvider03, p);
				break;
			case 2:
				assertEquals(defaultProvider01, p);
				break;
			}
			cnt++;
		}
		
		Optional<JerseyApplicationProvider> first = DispatcherHelper.getDefaultApplication(providers);
		assertNotNull(first);
		assertTrue(first.isPresent());
		assertEquals(defaultProvider02, first.get());
	}
	
	@Test
	public void IntSortTest() {
		Set<Integer> sorted = Stream.of(12, 20, 19, 4).sorted(Comparator.reverseOrder()).collect(Collectors.toSet());
		System.out.println("----SORTED-------");
		for (Integer i : sorted) {
			System.out.println("i = " + i);
		}
		System.out.println("-----------------");
		Set<Integer> is = Stream.of(12, 20, 19, 4).sorted(Comparator.reverseOrder()).collect(Collectors.toCollection(LinkedHashSet::new));
		System.out.println("----IS:-----------");
		for (Integer i : is) {
			System.out.println("i = " + i);
		}
		System.out.println("-----------------");
	}
	
	/**
	 * Creates an application provider
	 * @param name provider name
	 * @param rank service rank
	 * @param serviceId the service id
	 * @return the JerseyApplicationProvider instance 
	 */
	private JerseyApplicationProvider createApplicationProvider(String name, Integer rank, Long serviceId) {
		Map<String, Object> properties = new HashMap<String, Object>();
		if (name != null) {
			properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, name);
		}
		if (rank != null) {
			properties.put(Constants.SERVICE_RANKING, rank);
		}
		if (serviceId != null) {
			properties.put(Constants.SERVICE_ID, serviceId);
		}
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), properties);
		return provider;
	}

}
