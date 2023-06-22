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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsWhiteboardDispatcher;
import org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider;
import org.eclipse.osgitech.rest.resources.TestLegacyApplication;
import org.eclipse.osgitech.rest.resources.TestResource;
import org.eclipse.osgitech.rest.runtime.JerseyWhiteboardDispatcher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceObjects;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.core.Application;

/**
 * Tests the whiteboard dispatcher
 * @author Mark Hoffmann
 * @since 12.10.2017
 */
@ExtendWith(MockitoExtension.class)
public class JakartarsWhiteboardDispatcherTest {

	@Mock
	private JakartarsWhiteboardProvider whiteboard;

	//@Mock
	private ServiceObjects<Object> serviceObject;
	
	//@Before
	public void before() {
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true);
	}
	
	/**
	 * Tests the dispatcher in not ready state
	 */
	//@Test(expected=IllegalStateException.class)
	public void testDispatcherNotReady() {
		JakartarsWhiteboardDispatcher dispatcher = new JerseyWhiteboardDispatcher();
		assertFalse(dispatcher.isDispatching());
		dispatcher.dispatch();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, never()).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher without any action
	 */
	//@Test
	public void testDispatcherReady() {
		JakartarsWhiteboardDispatcher dispatcher = new JerseyWhiteboardDispatcher();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		
		Map<String, Object> defaultProperties = new HashMap<>();
		defaultProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, ".default");
		defaultProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "/");
		dispatcher.addApplication(new Application(), defaultProperties);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		// default application should be registered
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a legacy application
	 */
	//@Test
	public void testDispatcherLegacyApplicationAdd() {
		JakartarsWhiteboardDispatcher dispatcher = new JerseyWhiteboardDispatcher();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.emptyMap();
			}
		});
		/* 
		 * isRegistered call
		 * 1. addApplication no name: false, 
		 * 2. addApplication test: add no name again: false,
		 * 2. addApplication test: add test: false,
		 * 4. Deactivate application no name: false
		 * 5. Deactivate test - application: true
		 */
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, false, false, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, never()).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		Application application = new TestLegacyApplication();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// no further application registered because of missing properties
		Mockito.verify(whiteboard, never()).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		assertEquals(1, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(2, dispatcher.getApplications().size());
		
		// no further application registered because it is legacy
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a legacy application
	 */
	//@Test
	public void testDispatcherLegacyApplicationRemove() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.emptyMap();
			}
		});
		/* 
		 * isRegistered call
		 * 1. addApplication test: add test: false,
		 * 2. removeApplication test: remove test: true,
		 */
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(10));
		Application application = new TestLegacyApplication();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// no further application registered
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		assertEquals(1, dispatcher.getApplications().size());
		dispatcher.removeApplication(application, appProperties);
		assertEquals(0, dispatcher.getApplications().size());
		
		// no further application registered
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
	}
	
	private JakartarsWhiteboardDispatcher createDispatcherWithDefaultApplication() {
		JerseyWhiteboardDispatcher dispatcher = new JerseyWhiteboardDispatcher();
		
		//actually register default Application
		Map<String, Object> defaultProperties = new HashMap<>();
		defaultProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, ".default");
		defaultProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "/");
		dispatcher.addApplication(new Application(), defaultProperties);
		
		return dispatcher;
	}

	/**
	 * Tests the dispatcher with a legacy application
	 */
	//@Test
	public void testDispatcherReloadDefaultApplication() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.emptyMap();
			}
		});
		/* 
		 * 1. addApplication test: false, 
		 * 2. addResource: Application test: true,
		 * 3. Deactivate test - application: true
		 */
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, .test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource = new TestResource();
		when(serviceObject.getService()).thenReturn(resource);
		Map<String, Object> resProperties = new HashMap<>();
		resProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties);
		assertEquals(1, dispatcher.getResources().size());
		
		// .default is registered and resource was added to default
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a legacy application
	 */
	//@Test
	public void testDispatcherLegacyApplicationAddRemoveResourceDefault() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.emptyMap();
			}
		});
		/* 
		 * 1. addApplication test: false, 
		 * 2. addResource: Application test: true,
		 * 3. Deactivate test - application: true
		 */
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, true, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		Application application = new TestLegacyApplication();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, .test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource = new TestResource();
		when(serviceObject.getService()).thenReturn(resource);
		Map<String, Object> resProperties = new HashMap<>();
		resProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties);
		assertEquals(1, dispatcher.getResources().size());
		
		// .default is registered and resource was added to default
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		dispatcher.removeResource(resProperties);
		
		// .default is registered and resource was added to default
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a legacy application
	 */
	//@Test
	public void testDispatcherApplicationAddRemoveResource01() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.emptyMap();
			}
		});
		/* 
		 * 1. addApplication test: false, 
		 * 2. addResource: Application test: false,
		 * 3. addResource2; Application test: true,
		 * 4. removeResource Application test: true,
		 * 5. Deactivate test - application: true
		 */
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, false, true, true, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "testApp");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(100));
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource01 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource01);
		Map<String, Object> resProperties01 = new HashMap<>();
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res01");
		resProperties01.put(Constants.SERVICE_ID, Long.valueOf(10));
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		// .default is registered and resource was added to testApp, which is not empty and will be registered
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource02 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource02);
		Map<String, Object> resProperties02 = new HashMap<>();
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res02");
		resProperties02.put(Constants.SERVICE_ID, Long.valueOf(20));
		assertEquals(1, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties02);
		assertEquals(2, dispatcher.getResources().size());
		
		// .default is registered and resource was added to testApp, which is not empty and will be registered
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		assertEquals(2, dispatcher.getResources().size());
		dispatcher.removeResource(resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		// .default is registered and resource was added to default
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a legacy application
	 */
	//@Test
	public void testDispatcherApplicationAddRemoveResource02() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.emptyMap();
			}
		});
		/* 
		 * 1. addApplication test: false, 
		 * 2. addResource: Application test: false,
		 * 3. addResource2; Application test: true,
		 * 4. removeResource Application test: true,
		 * 5. Deactivate test - application: true
		 */
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, true, true, true, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "testApp");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(100));
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource01 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource01);
		Map<String, Object> resProperties01 = new HashMap<>();
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=*)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res01");
		resProperties01.put(Constants.SERVICE_ID, Long.valueOf(10));
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. testApp is not registered but empty.
		 * Resource1 was added to testApp and .default.
		 * So .default will be reloaded.
		 * testApp is not empty anymore and will be registered
		 */
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).reloadApplication(Mockito.any());
		
		TestResource resource02 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource02);
		Map<String, Object> resProperties02 = new HashMap<>();
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res02");
		resProperties02.put(Constants.SERVICE_ID, Long.valueOf(20));
		assertEquals(1, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties02);
		assertEquals(2, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was added to testApp.
		 * testApp should be reloaded
		 */
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(3)).reloadApplication(Mockito.any());
		
		assertEquals(2, dispatcher.getResources().size());
		dispatcher.removeResource(resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource1 was removed from testApp and .default.
		 * testAppand .default should be reloaded
		 */
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(5)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(5)).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a legacy application
	 */
	//@Test
	public void testDispatcherApplicationAddRemoveResource03() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.emptyMap();
			}
		});
		/* 
		 * 1. addApplication test: false, 
		 * 2. addResource: Application test: false,
		 * 3. addResource2; Application test: true,
		 * 4. removeResource Application test: true,
		 * 5. Deactivate test - application: true
		 */
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, false, true, true, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "testApp");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(100));
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource01 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource01);
		Map<String, Object> resProperties01 = new HashMap<>();
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=*)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res01");
		resProperties01.put(Constants.SERVICE_ID, Long.valueOf(10));
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. testApp is not registered but empty.
		 * Resource was added to testApp and .default.
		 * So default will be reloaded.
		 * testApp wis not empty anymore and will be registered
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		TestResource resource02 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource02);
		Map<String, Object> resProperties02 = new HashMap<>();
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res02");
		resProperties02.put(Constants.SERVICE_ID, Long.valueOf(20));
		assertEquals(1, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties02);
		assertEquals(2, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was added to testApp.
		 * testApp should be reloaded
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).reloadApplication(Mockito.any());
		
		assertEquals(2, dispatcher.getResources().size());
		dispatcher.removeResource(resProperties02);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was removed from testApp.
		 * testApp should be reloaded
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(3)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(3)).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a whiteboard target filter that does not match to the application
	 */
	//@Test
	public void testDispatcherApplicationAddRemoveResourceWhiteboardTarget01() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				Map<String, Object> wbProperties = new HashMap<>();
				wbProperties.put("customer", "my");
				return wbProperties;
			}
		});
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "testApp");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(name=hello)");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(100));
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, test is empty
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource01 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource01);
		Map<String, Object> resProperties01 = new HashMap<>();
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=*)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res01");
		resProperties01.put(Constants.SERVICE_ID, Long.valueOf(10));
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. 
		 * testApp is not registered but empty.
		 * Resource was added .default, testApp doesn't match the whiteboard target filter
		 * So default will be reloaded.
		 */
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource02 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource02);
		Map<String, Object> resProperties02 = new HashMap<>();
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res02");
		resProperties02.put(Constants.SERVICE_ID, Long.valueOf(20));
		assertEquals(1, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties02);
		assertEquals(2, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. Resource2 was added to testApp.
		 * Nothing else happens
		 */
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		assertEquals(2, dispatcher.getResources().size());
		dispatcher.removeResource(resProperties02);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. Resource2 was removed from testApp.
		 * Nothing else happens
		 */
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a whiteboard target filter that matches to the application
	 */
	//@Test
	public void testDispatcherApplicationAddRemoveResourceWhiteboardTarget02() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				Map<String, Object> wbProperties = new HashMap<>();
				wbProperties.put("customer", "my");
				return wbProperties;
			}
		});
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, false, true, true, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "testApp");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(customer=my)");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(100));
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource01 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource01);
		Map<String, Object> resProperties01 = new HashMap<>();
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=*)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res01");
		resProperties01.put(Constants.SERVICE_ID, Long.valueOf(10));
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. testApp is not registered but empty.
		 * Resource was added to testApp and .default.
		 * So default will be reloaded.
		 * testApp wis not empty anymore and will be registered
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		TestResource resource02 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource02);
		Map<String, Object> resProperties02 = new HashMap<>();
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res02");
		resProperties02.put(Constants.SERVICE_ID, Long.valueOf(20));
		assertEquals(1, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties02);
		assertEquals(2, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was added to testApp.
		 * testApp should be reloaded
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).reloadApplication(Mockito.any());
		
		assertEquals(2, dispatcher.getResources().size());
		dispatcher.removeResource(resProperties02);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was removed from testApp.
		 * testApp should be reloaded
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(3)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(3)).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a whiteboard target filter that matches to the application but a resource target filter does not match
	 */
	//@Test
	public void testDispatcherApplicationAddRemoveResourceWhiteboardTarget03() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			//@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				Map<String, Object> wbProperties = new HashMap<>();
				wbProperties.put("customer", "my");
				return wbProperties;
			}
		});
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, true, false, true, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "testApp");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(customer=my)");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(100));
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource01 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource01);
		Map<String, Object> resProperties01 = new HashMap<>();
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(test=my)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=*)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res01");
		resProperties01.put(Constants.SERVICE_ID, Long.valueOf(10));
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. testApp is registered but empty.
		 * Resource was not added to testApp and .default because the filter does not match
		 */
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource02 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource02);
		Map<String, Object> resProperties02 = new HashMap<>();
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res02");
		resProperties02.put(Constants.SERVICE_ID, Long.valueOf(20));
		assertEquals(1, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties02);
		assertEquals(2, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was added to testApp that will be registered now.
		 * testApp should be registered
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		assertEquals(2, dispatcher.getResources().size());
		dispatcher.removeResource(resProperties02);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was removed from testApp.
		 * testApp should be unregistered because it is empty
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
	}
	
	/**
	 * Tests the dispatcher with a whiteboard target filter that matches to the application and
	 * the resource target filter matches
	 */
	//@Test
	public void testDispatcherApplicationAddRemoveResourceWhiteboardTarget04() {
		JakartarsWhiteboardDispatcher dispatcher = createDispatcherWithDefaultApplication();
		assertFalse(dispatcher.isDispatching());
		assertNotNull(whiteboard);
		dispatcher.setWhiteboardProvider(whiteboard);
		// whiteboard has no properties
		when(whiteboard.getProperties()).thenAnswer(new Answer<Map<String, Object>>() {
			@Override
			public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
				Map<String, Object> wbProperties = new HashMap<>();
				wbProperties.put("customer", "my");
				return wbProperties;
			}
		});
		when(whiteboard.isRegistered(Mockito.any(JakartarsApplicationProvider.class))).thenReturn(true, false, false, false, true, true, true);
		
		dispatcher.dispatch();
		assertTrue(dispatcher.isDispatching());
		
		// register default application
		Mockito.verify(whiteboard, times(1)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "testApp");
		appProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(customer=my)");
		appProperties.put(Constants.SERVICE_ID, Long.valueOf(100));
		Application application = new Application();
		
		assertEquals(0, dispatcher.getApplications().size());
		dispatcher.addApplication(application, appProperties);
		assertEquals(1, dispatcher.getApplications().size());
		
		// .default is registered, test is empty
		Mockito.verify(whiteboard, times(2)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).reloadApplication(Mockito.any());
		
		TestResource resource01 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource01);
		Map<String, Object> resProperties01 = new HashMap<>();
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(customer=my)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=*)");
		resProperties01.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res01");
		resProperties01.put(Constants.SERVICE_ID, Long.valueOf(10));
		assertEquals(0, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties01);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default is registered. testApp is not registered but empty.
		 * Resource was not added to testApp and .default because the filter does not match
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(1)).reloadApplication(Mockito.any());
		
		TestResource resource02 = new TestResource();
		when(serviceObject.getService()).thenReturn(resource02);
		Map<String, Object> resProperties02 = new HashMap<>();
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=testApp)");
		resProperties02.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res02");
		resProperties02.put(Constants.SERVICE_ID, Long.valueOf(20));
		assertEquals(1, dispatcher.getResources().size());
		dispatcher.addResource(serviceObject, resProperties02);
		assertEquals(2, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was added to testApp that will be registered now.
		 * testApp should be registered
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).reloadApplication(Mockito.any());
		
		assertEquals(2, dispatcher.getResources().size());
		dispatcher.removeResource(resProperties02);
		assertEquals(1, dispatcher.getResources().size());
		
		/* 
		 * .default and testApp are registered. Resource2 was removed from testApp.
		 * testApp should be unregistered because it is empty
		 */
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, never()).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(3)).reloadApplication(Mockito.any());
		
		dispatcher.deactivate();
		assertFalse(dispatcher.isDispatching());
		
		Mockito.verify(whiteboard, times(3)).registerApplication(Mockito.any());
		Mockito.verify(whiteboard, times(2)).unregisterApplication(Mockito.any());
		Mockito.verify(whiteboard, times(3)).reloadApplication(Mockito.any());
	}

}
