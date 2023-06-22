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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Application;

/**
 * 
 * @author Mark Hoffmann
 * @since 21.10.2017
 */
public class JerseyHelperTests {
	
	private static class TestApplication extends Application {
		
		private Set<Class<?>> classes = new HashSet<>();
		private Set<Object> singletons = new HashSet<>();
		private Map<String, Object> properties = new HashMap<>();
		
		@Override
		public Set<Class<?>> getClasses() {
			return classes;
		}
		
		@Override
		public Set<Object> getSingletons() {
			return singletons;
		}
		
		@Override
		public Map<String, Object> getProperties() {
			return properties;
		}
		
	}
	
	/**
	 * Test method for {@link org.eclipse.osgitech.rest.helper.JerseyHelper#isEmpty(jakarta.ws.rs.core.Application)}.
	 */
	@Test
	public void testIsEmpty() {
		assertTrue(JerseyHelper.isEmpty(new TestApplication()));
		TestApplication app = new TestApplication();
	
		app.getProperties().put("test", "me");
		assertTrue(JerseyHelper.isEmpty(app));
		app.getProperties().clear();
		assertTrue(JerseyHelper.isEmpty(app));

		app.getClasses().add(String.class);
		assertFalse(JerseyHelper.isEmpty(app));
		app.getClasses().clear();
		assertTrue(JerseyHelper.isEmpty(app));
		
		app.getSingletons().add(new String("test"));
		assertFalse(JerseyHelper.isEmpty(app));
		app.getSingletons().clear();
		assertTrue(JerseyHelper.isEmpty(app));
	}

	@Test
	public void testStringPlus() {
		Map<String, Object> properties = new HashMap<>();
		assertNull(JerseyHelper.getStringPlusProperty(null, null));
		assertNull(JerseyHelper.getStringPlusProperty("test", null));
		assertNull(JerseyHelper.getStringPlusProperty(null, properties));
		
		properties.put("test", Integer.valueOf(1));
		assertNull(JerseyHelper.getStringPlusProperty("test", properties));
		properties.put("test", "2");
		String[] result = new String[] {"2"};
		assertArrayEquals(result, JerseyHelper.getStringPlusProperty("test", properties));
		
		properties.put("test", new Integer[] {Integer.valueOf(1), Integer.valueOf(2)});
		assertNull(JerseyHelper.getStringPlusProperty("test", properties));
		properties.put("test", new String[] {"3", "4"});
		result = new String[] {"3", "4"};
		assertArrayEquals(result, JerseyHelper.getStringPlusProperty("test", properties));
		
		properties.put("test", List.of(Integer.valueOf(1), Integer.valueOf(2)));
		result = new String[] {"1", "2"};
		assertArrayEquals(result, JerseyHelper.getStringPlusProperty("test", properties));
		properties.put("test", List.of("5", "6"));
		result = new String[] {"5", "6"};
		assertArrayEquals(result, JerseyHelper.getStringPlusProperty("test", properties));
		properties.put("test", List.of(Integer.valueOf(2), "Blub", "8", Integer.valueOf(9)));
		result = new String[] {"2", "Blub", "8", "9"};
		assertArrayEquals(result, JerseyHelper.getStringPlusProperty("test", properties));
		
		properties.put("test", new ArrayList<Integer>());
		result = new String[0];
		assertArrayEquals(result, JerseyHelper.getStringPlusProperty("test", properties));
		properties.put("test", new ArrayList<String>());
		result = new String[0];
		assertArrayEquals(result, JerseyHelper.getStringPlusProperty("test", properties));
	}
}
