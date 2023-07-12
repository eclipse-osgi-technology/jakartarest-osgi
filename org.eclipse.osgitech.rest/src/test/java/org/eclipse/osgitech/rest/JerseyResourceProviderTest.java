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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.core.Application;

import org.eclipse.osgitech.rest.resources.TestResource;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.ServiceObjects;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.FailedResourceDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * 
 * @author Mark Hoffmann
 * @since 21.09.2017
 */
@ExtendWith(MockitoExtension.class)
public class JerseyResourceProviderTest {

	@Mock
	private ServiceObjects<Object> serviceObject;

	@Test
	public void testApplicationSelect() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla)");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "test");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertFalse(dto instanceof FailedApplicationDTO);
		
		assertEquals("/test", provider.getPath());
		assertEquals("test", provider.getName());
		
		Map<String, Object> resourceProperties = new HashMap<>();
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		when(serviceObject.getService()).thenReturn(new TestResource());
		JerseyResourceProvider resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		BaseDTO resourceDto = resourceProvider.getResourceDTO();
		assertFalse(resourceDto instanceof FailedResourceDTO);
		assertTrue(resourceProvider.isSingleton());
		
		assertNotNull(resourceProvider.getName());
		assertEquals(TestResource.class, resourceProvider.getObjectClass());
		
//		In the current implementation the canHandleApplication is called before adding internally the resource,
//		but the addResource alone does not check the canHandleApplication thus it returns true also if the resource 
//		cannot handle the application
		assertFalse(resourceProvider.canHandleApplication(provider));
//		assertFalse(provider.addResource(resourceProvider));
		
		// invalid application filter
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "test");
		resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		assertFalse(resourceProvider.canHandleApplication(provider));
//		assertFalse(provider.addResource(resourceProvider));
		
		resourceDto = resourceProvider.getResourceDTO();
		assertTrue(resourceDto instanceof FailedResourceDTO);
		FailedResourceDTO failedDto = (FailedResourceDTO) resourceDto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		assertTrue(resourceProvider.isSingleton());
		
		// application filter does not match
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(name=xy)");
		resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		assertFalse(resourceProvider.canHandleApplication(provider));
//		assertFalse(provider.addResource(resourceProvider));
		
		resourceDto = resourceProvider.getResourceDTO();
		assertFalse(resourceDto instanceof FailedResourceDTO);
		assertTrue(resourceProvider.isSingleton());
		
		// application filter matches
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(" + JakartarsWhiteboardConstants.JAKARTA_RS_NAME + "=test)");
		resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		assertTrue(resourceProvider.canHandleApplication(provider));
//		assertTrue(provider.addResource(resourceProvider));
		resourceDto = resourceProvider.getResourceDTO();
		assertFalse(resourceDto instanceof FailedResourceDTO);
		assertTrue(resourceProvider.isSingleton());
	}
	
	@Test
	public void testResourceProviderPrototype() {
		
		when(serviceObject.getService()).thenReturn(new TestResource());
		JerseyResourceProvider resourceProvider = new JerseyResourceProvider(serviceObject, Collections.emptyMap());
		BaseDTO resourceDto = resourceProvider.getResourceDTO();
		assertTrue(resourceDto instanceof FailedResourceDTO);
		FailedResourceDTO failedDto = (FailedResourceDTO) resourceDto;
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, failedDto.failureReason);
		assertTrue(resourceProvider.isSingleton());
		
		Map<String, Object> resourceProperties = new HashMap<>();
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		
		resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		resourceDto = resourceProvider.getResourceDTO();
		assertFalse(resourceDto instanceof FailedResourceDTO);
		assertTrue(resourceProvider.isSingleton());
		
		assertNotNull(resourceProvider.getName());
		assertEquals(TestResource.class, resourceProvider.getObjectClass());
		
		resourceProperties.put("service.scope", "prototype");
		
		resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		resourceDto = resourceProvider.getResourceDTO();
		assertFalse(resourceDto instanceof FailedResourceDTO);
		assertFalse(resourceProvider.isSingleton());
		
		assertNotNull(resourceProvider.getName());
		assertEquals(TestResource.class, resourceProvider.getObjectClass());
				
	}
	
	@Test
	public void testResourceProviderName() {

		when(serviceObject.getService()).thenReturn(new TestResource());
		JerseyResourceProvider resourceProvider = new JerseyResourceProvider(serviceObject, Collections.emptyMap());
		BaseDTO resourceDto = resourceProvider.getResourceDTO();
		assertTrue(resourceDto instanceof FailedResourceDTO);
		FailedResourceDTO failedDto = (FailedResourceDTO) resourceDto;
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, failedDto.failureReason);
		assertTrue(resourceProvider.isSingleton());
		
		Map<String, Object> resourceProperties = new HashMap<>();
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		
		resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		resourceDto = resourceProvider.getResourceDTO();
		assertFalse(resourceDto instanceof FailedResourceDTO);
		assertTrue(resourceProvider.isSingleton());
		
		assertNotNull(resourceProvider.getName());
		assertNotEquals("test", resourceProvider.getName());
		assertEquals(TestResource.class, resourceProvider.getObjectClass());
		
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "test");
		
		resourceProvider = new JerseyResourceProvider(serviceObject, resourceProperties);
		
		resourceDto = resourceProvider.getResourceDTO();
		assertFalse(resourceDto instanceof FailedResourceDTO);
		assertTrue(resourceProvider.isSingleton());
		assertEquals("test", resourceProvider.getName());
		
		assertNotNull(resourceProvider.getName());
		assertEquals(TestResource.class, resourceProvider.getObjectClass());
		
	}
	
}
