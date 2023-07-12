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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.MessageBodyReader;

import org.eclipse.osgitech.rest.resources.TestExtension;
import org.eclipse.osgitech.rest.resources.TestResource;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyExtensionProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceObjects;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseDTO;
import org.osgi.service.jakartars.runtime.dto.BaseExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.ExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.FailedExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * Tests the basics of the {@link JakartarsProvider} interface
 * @author Mark Hoffmann
 * @since 13.10.2017
 */
@ExtendWith(MockitoExtension.class)
public class JerseyProviderTest {

	@Mock
	private ServiceObjects<Object> serviceObject;
	
	/**
	 * Application has a custom name implementation
	 */
	@Test
	public void testNameApplication() {
		Map<String, Object> properties = new HashMap<>();
		JerseyApplicationProvider appProvider = new JerseyApplicationProvider(new Application(), properties);
		// generated name
		assertTrue(appProvider.getName().startsWith("."));
		BaseApplicationDTO appDTO = appProvider.getApplicationDTO();
		assertNotNull(appDTO);
		assertTrue(appDTO instanceof FailedApplicationDTO);
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, ((FailedApplicationDTO)appDTO).failureReason);

		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		appProvider = new JerseyApplicationProvider(new Application(), properties);
		// generated name
		assertTrue(appProvider.getName().startsWith("."));
		appDTO = appProvider.getApplicationDTO();
		assertNotNull(appDTO);
		assertTrue(appDTO instanceof ApplicationDTO);

		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, ".test");
		appProvider = new JerseyApplicationProvider(new Application(), properties);

		// generated name
		assertEquals(".test", appProvider.getName());
		appDTO = appProvider.getApplicationDTO();
		assertNotNull(appDTO);
		assertTrue(appDTO instanceof FailedApplicationDTO);
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, ((FailedApplicationDTO)appDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "osgitest");
		appProvider = new JerseyApplicationProvider(new Application(), properties);
		
		// generated name
		assertEquals("osgitest", appProvider.getName());
		appDTO = appProvider.getApplicationDTO();
		assertNotNull(appDTO);
		assertTrue(appDTO instanceof FailedApplicationDTO);
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, ((FailedApplicationDTO)appDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "mytest");
		appProvider = new JerseyApplicationProvider(new Application(), properties);
		
		// generated name
		assertEquals("mytest", appProvider.getName());
		appDTO = appProvider.getApplicationDTO();
		assertNotNull(appDTO);
		assertTrue(appDTO instanceof ApplicationDTO);
	}
	
	/**
	 * Resources and extensions share their name implementation
	 */
	@Test
	public void testNameExtension() {
		Map<String, Object> properties = new HashMap<>();
//		This should advertise one of the valid extension types to be considered an extension
//		properties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
		properties.put(Constants.OBJECTCLASS, new String[] {MessageBodyReader.class.getName()});		
		
		when(serviceObject.getService()).thenReturn(new TestExtension());
		JerseyExtensionProvider extProvider = new JerseyExtensionProvider(serviceObject, properties);
		// generated name
		assertTrue(extProvider.getName().startsWith("."));
		BaseExtensionDTO extDTO = extProvider.getExtensionDTO();
		assertNotNull(extDTO);
		assertTrue(extDTO instanceof FailedExtensionDTO);
		assertEquals(DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE, ((FailedExtensionDTO)extDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "false");
		extProvider = new JerseyExtensionProvider(serviceObject, properties);
		// generated name
		assertNotNull(extProvider.getName());
		extDTO = extProvider.getExtensionDTO();
		assertNotNull(extDTO);
		assertTrue(extDTO instanceof FailedExtensionDTO);
		assertEquals(DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE, ((FailedExtensionDTO)extDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, ".test");
		extProvider = new JerseyExtensionProvider(serviceObject, properties);
		
		// generated name
		assertEquals(".test", extProvider.getName());
		extDTO = extProvider.getExtensionDTO();
		assertNotNull(extDTO);
		assertTrue(extDTO instanceof FailedExtensionDTO);
		assertEquals(DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE, ((FailedExtensionDTO)extDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "test");
		extProvider = new JerseyExtensionProvider(serviceObject, properties);
		
		// generated name
		assertEquals("test", extProvider.getName());
		extDTO = extProvider.getExtensionDTO();
		assertNotNull(extDTO);
		assertTrue(extDTO instanceof FailedExtensionDTO);
		assertEquals(DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE, ((FailedExtensionDTO)extDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "true");
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, ".test");
		extProvider = new JerseyExtensionProvider(serviceObject, properties);
		
		// generated name
		assertEquals(".test", extProvider.getName());
		extDTO = extProvider.getExtensionDTO();
		assertNotNull(extDTO);
		assertTrue(extDTO instanceof FailedExtensionDTO);
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, ((FailedExtensionDTO)extDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "osgitest");
		extProvider = new JerseyExtensionProvider(serviceObject, properties);
		
		// generated name
		assertEquals("osgitest", extProvider.getName());
		extDTO = extProvider.getExtensionDTO();
		assertNotNull(extDTO);
		assertTrue(extDTO instanceof FailedExtensionDTO);
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, ((FailedExtensionDTO)extDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "mytest");
		extProvider = new JerseyExtensionProvider(serviceObject, properties);
		
		// generated name
		assertEquals("mytest", extProvider.getName());
		extDTO = extProvider.getExtensionDTO();
		assertNotNull(extDTO);
		assertTrue(extDTO instanceof ExtensionDTO);
	}
	
	/**
	 * Resources and extensions share their name implementation
	 */
	@Test
	public void testNameResource() {
		Map<String, Object> properties = new HashMap<>();
		when(serviceObject.getService()).thenReturn(new TestResource());
		JerseyResourceProvider resProvider = new JerseyResourceProvider(serviceObject, properties);
		// generated name
		assertTrue(resProvider.getName().startsWith("."));
		BaseDTO resDTO = resProvider.getResourceDTO();
		assertNotNull(resDTO);
		assertTrue(resDTO instanceof FailedResourceDTO);
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, ((FailedResourceDTO)resDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "false");
		resProvider = new JerseyResourceProvider(serviceObject, properties);
		// generated name
		assertNotNull(resProvider.getName());
		resDTO = resProvider.getResourceDTO();
		assertNotNull(resDTO);
		assertTrue(resDTO instanceof FailedResourceDTO);
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, ((FailedResourceDTO)resDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, ".test");
		resProvider = new JerseyResourceProvider(serviceObject, properties);
		
		// generated name
		assertEquals(".test", resProvider.getName());
		resDTO = resProvider.getResourceDTO();
		assertNotNull(resDTO);
		assertTrue(resDTO instanceof FailedResourceDTO);
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, ((FailedResourceDTO)resDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "test");
		resProvider = new JerseyResourceProvider(serviceObject, properties);
		
		// generated name
		assertEquals("test", resProvider.getName());
		resDTO = resProvider.getResourceDTO();
		assertNotNull(resDTO);
		assertTrue(resDTO instanceof FailedResourceDTO);
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, ((FailedResourceDTO)resDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, ".test");
		resProvider = new JerseyResourceProvider(serviceObject, properties);
		
		// generated name
		assertEquals(".test", resProvider.getName());
		resDTO = resProvider.getResourceDTO();
		assertNotNull(resDTO);
		assertTrue(resDTO instanceof FailedResourceDTO);
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, ((FailedResourceDTO)resDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "osgitest");
		resProvider = new JerseyResourceProvider(serviceObject, properties);
		
		// generated name
		assertEquals("osgitest", resProvider.getName());
		resDTO = resProvider.getResourceDTO();
		assertNotNull(resDTO);
		assertTrue(resDTO instanceof FailedResourceDTO);
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, ((FailedResourceDTO)resDTO).failureReason);
		
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "mytest");
		resProvider = new JerseyResourceProvider(serviceObject, properties);
		
		// generated name
		assertEquals("mytest", resProvider.getName());
		resDTO = resProvider.getResourceDTO();
		assertNotNull(resDTO);
		assertTrue(resDTO instanceof ResourceDTO);
	}

}
