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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;

import jakarta.ws.rs.core.Application;

import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider;
import org.eclipse.osgitech.rest.resources.TestApplPathApplication;
import org.eclipse.osgitech.rest.resources.TestExtension;
import org.eclipse.osgitech.rest.resources.TestPathApplication;
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
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.ExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.FailedExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceMethodInfoDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * Tests the DTO converter
 * @author Mark Hoffmann
 * @since 14.07.2017
 */
@ExtendWith(MockitoExtension.class)
public class DTOConverterTest {
	
	@Mock
	private ServiceObjects<Object> serviceObject;
	
	/**
	 * Tests conversion of a failed application DTO
	 */
	@Test
	public void testToFailedApplicationDTO() {
		Map<String, Object> properties = new Hashtable<>();
		
		JakartarsApplicationProvider resourceProvider = new JerseyApplicationProvider(new Application(), properties);
		
		FailedApplicationDTO dto = DTOConverter.toFailedApplicationDTO(resourceProvider, DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE);
		assertNotNull(dto);
		assertNotNull(dto.name);
		assertTrue(dto.name.startsWith("."));
		assertEquals(-1, dto.serviceId);
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, dto.failureReason);
		
		properties.put(Constants.SERVICE_ID, Long.valueOf(12));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "MyApp");
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		
		resourceProvider = new JerseyApplicationProvider(new Application(), properties);
		dto = DTOConverter.toFailedApplicationDTO(resourceProvider, DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE);
		
		assertNotNull(dto);
		assertEquals("/test/*", dto.base);
		assertEquals("MyApp", dto.name);
		assertEquals(12, dto.serviceId);
		assertEquals(DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE, dto.failureReason);
		
	}
	
	/**
	 * Tests conversion of an application DTO
	 */
	@Test
	public void testToApplicationDTO() {
		Map<String, Object> properties = new Hashtable<>();
		
		JakartarsApplicationProvider resourceProvider = new JerseyApplicationProvider(new Application(), properties);
		
		ApplicationDTO dto = DTOConverter.toApplicationDTO(resourceProvider);
		assertNotNull(dto);
		assertNotNull(dto.name);
		assertTrue(dto.name.startsWith("."));
		assertEquals(-1, dto.serviceId);
		
		properties.put(Constants.SERVICE_ID, Long.valueOf(12));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "MyApp");
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		
		resourceProvider = new JerseyApplicationProvider(new Application(), properties);
		dto = DTOConverter.toApplicationDTO(resourceProvider);
		
		assertNotNull(dto);
		assertEquals("/test/", dto.base);
		assertEquals("MyApp", dto.name);
		assertEquals(12, dto.serviceId);

		resourceProvider = new JerseyApplicationProvider(new TestApplPathApplication(), properties);
		dto = DTOConverter.toApplicationDTO(resourceProvider);

		assertNotNull(dto);
		assertEquals("/test/applpath/", dto.base);

		// TODO: test resourceMethofs - The RequestPaths handled by statically defined
		// resources in this Application

		resourceProvider = new JerseyApplicationProvider(new TestPathApplication(), properties);
		dto = DTOConverter.toApplicationDTO(resourceProvider);

		assertNotNull(dto);
		assertEquals("/test/", dto.base);
	}
	
	/**
	 * Tests conversion of a failed resource DTO
	 */
	@Test
	public void testToFailedResourceDTO() {
		TestResource resource = new TestResource();
		Map<String, Object> properties = new Hashtable<>();
		
		when(serviceObject.getService()).thenReturn(resource);
		JakartarsResourceProvider resourceProvider = new JerseyResourceProvider<Object>(serviceObject, properties);
		
		FailedResourceDTO dto = DTOConverter.toFailedResourceDTO(resourceProvider, DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE);
		assertNotNull(dto);
		assertTrue(dto.name.startsWith("."));
		assertEquals(-1, dto.serviceId);
		assertEquals(DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE, dto.failureReason);
		
		properties.put(Constants.SERVICE_ID, Long.valueOf(12));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "Myresource");
		
		resourceProvider = new JerseyResourceProvider<Object>(serviceObject, properties);
		dto = DTOConverter.toFailedResourceDTO(resourceProvider, DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE);
		
		assertNotNull(dto);
		assertEquals("Myresource", dto.name);
		assertEquals(12, dto.serviceId);
		assertEquals(DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE, dto.failureReason);
		
	}
	
	/**
	 *  Test method for {@link org.eclipselabs.osgi.jersey.dto.DTOConverter#toResourceDTO(java.lang.Objectm java.util.Dictionary)}.
	 */
	@Test
	public void testToResourceDTO() {
		TestResource resource = new TestResource();
		Map<String, Object> properties = new Hashtable<>();
		when(serviceObject.getService()).thenReturn(resource);
		JakartarsResourceProvider resourceProvider = new JerseyResourceProvider<Object>(serviceObject, properties);
		
		ResourceDTO dto = DTOConverter.toResourceDTO(resourceProvider);
		assertNotNull(dto);
		assertTrue(dto.name.startsWith("."));
		assertEquals(-1, dto.serviceId);
		ResourceMethodInfoDTO[] methodInfoDTOs = dto.resourceMethods;
		assertNotNull(methodInfoDTOs);
		assertEquals(2, methodInfoDTOs.length);
		
		properties.put(Constants.SERVICE_ID, Long.valueOf(12));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "Myresource");
		
		resourceProvider = new JerseyResourceProvider<Object>(serviceObject, properties);
		dto = DTOConverter.toResourceDTO(resourceProvider);
		
		assertNotNull(dto);
		assertEquals("Myresource", dto.name);
		assertEquals(12, dto.serviceId);
		methodInfoDTOs = dto.resourceMethods;
		assertNotNull(methodInfoDTOs);
		assertEquals(2, methodInfoDTOs.length);
		
		properties = new Hashtable<>();
		properties.put(ComponentConstants.COMPONENT_ID, Long.valueOf(13));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "Myresource2");

		resourceProvider = new JerseyResourceProvider<Object>(serviceObject, properties);
		dto = DTOConverter.toResourceDTO(resourceProvider);
		
		assertNotNull(dto);
		assertEquals("Myresource2", dto.name);
		assertEquals(13, dto.serviceId);
		methodInfoDTOs = dto.resourceMethods;
		assertNotNull(methodInfoDTOs);
		assertEquals(2, methodInfoDTOs.length);
	}
	
	/**
	 * Tests conversion of a failed extension DTO
	 */
	@Test
	public void testToFailedExtensionDTO() {
		TestExtension extension = new TestExtension();
		Map<String, Object> properties = new Hashtable<>();
		properties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
		when(serviceObject.getService()).thenReturn(extension);
		JakartarsExtensionProvider extensionProvider = new JerseyExtensionProvider<Object>(serviceObject, properties);
		
		FailedExtensionDTO dto = DTOConverter.toFailedExtensionDTO(extensionProvider, DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE);
		assertNotNull(dto);
		assertTrue(dto.name.startsWith("."));
		assertEquals(-1, dto.serviceId);
		assertEquals(DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE, dto.failureReason);
		
		properties.put(Constants.SERVICE_ID, Long.valueOf(12));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "Myresource");
		
		extensionProvider = new JerseyExtensionProvider<Object>(serviceObject, properties);
		dto = DTOConverter.toFailedExtensionDTO(extensionProvider, DTOConstants.FAILURE_REASON_DUPLICATE_NAME);
		
		assertNotNull(dto);
		assertEquals("Myresource", dto.name);
		assertEquals(12, dto.serviceId);
		assertEquals(DTOConstants.FAILURE_REASON_DUPLICATE_NAME, dto.failureReason);
		
	}
	
	/**
	 *  Test method for {@link org.eclipselabs.osgi.jersey.dto.DTOConverter#toResourceDTO(java.lang.Objectm java.util.Dictionary)}.
	 */
	@Test
	public void testToExtensionDTO() {
		TestExtension extension = new TestExtension();
		Map<String, Object> properties = new Hashtable<>();
		properties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
		when(serviceObject.getService()).thenReturn(extension);
		JakartarsExtensionProvider extensionProvider = new JerseyExtensionProvider<Object>(serviceObject, properties);
		
		ExtensionDTO dto = DTOConverter.toExtensionDTO(extensionProvider);
		assertNotNull(dto);
		assertTrue(dto.name.startsWith("."));
		assertEquals(-1, dto.serviceId);
		
		properties.put(Constants.SERVICE_ID, Long.valueOf(12));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "Myresource");
		
		extensionProvider = new JerseyExtensionProvider<Object>(serviceObject, properties);
		dto = DTOConverter.toExtensionDTO(extensionProvider);
		
		assertNotNull(dto);
		assertEquals("Myresource", dto.name);
		assertEquals(12, dto.serviceId);
		assertEquals(2, dto.produces.length);
		assertEquals(1, dto.consumes.length);
		
		properties = new Hashtable<>();
		properties.put(ComponentConstants.COMPONENT_ID, Long.valueOf(13));
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "Myresource2");
		properties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
		extensionProvider = new JerseyExtensionProvider<Object>(serviceObject, properties);
		dto = DTOConverter.toExtensionDTO(extensionProvider);
		
		assertNotNull(dto);
		assertEquals("Myresource2", dto.name);
		assertEquals(13, dto.serviceId);
		assertEquals(2, dto.produces.length);
		assertEquals("xml", dto.produces[0]);
		assertEquals("json", dto.produces[1]);
		assertEquals(1, dto.consumes.length);
		assertEquals("test", dto.consumes[0]);
	}

	/**
	 * Test method for {@link org.eclipselabs.osgi.jersey.dto.DTOConverter#toResourceMethodInfoDTO(java.lang.reflect.Method)}.
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 */
	@Test
	public void testToResourceMethodInfoDTOs() throws NoSuchMethodException, SecurityException {
		TestResource resource = new TestResource();
		ResourceMethodInfoDTO[] methodInfoDTOsParsed = DTOConverter.getResourceMethodInfoDTOs(resource.getClass());
		assertNotNull(methodInfoDTOsParsed);
		assertEquals(2, methodInfoDTOsParsed.length);
	}
	
	/**
	 * Test method for {@link org.eclipselabs.osgi.jersey.dto.DTOConverter#toResourceMethodInfoDTO(java.lang.reflect.Method)}.
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 */
	@Test
	public void testToResourceMethodInfoDTO() throws NoSuchMethodException, SecurityException {
		Method method = TestResource.class.getDeclaredMethod("postMe", new Class[] {String.class});
		ResourceMethodInfoDTO dto = DTOConverter.toResourceMethodInfoDTO(method);
		assertNotNull(dto);
		assertEquals("pdf", dto.path);
		assertEquals("POST", dto.method);
		assertEquals(1, dto.consumingMimeType.length);
		assertEquals("pdf", dto.consumingMimeType[0]);
		assertEquals(1, dto.producingMimeType.length);
		assertEquals("text", dto.producingMimeType[0]);
		
		method = TestResource.class.getDeclaredMethod("postAndOut", new Class[0]);
		dto = DTOConverter.toResourceMethodInfoDTO(method);
		assertNotNull(dto);
		assertTrue(dto.method.contains("POST"));
		assertTrue(dto.method.contains("PUT"));
		assertFalse(dto.method.contains("DELETE"));
		assertFalse(dto.method.contains("HEAD"));
		assertFalse(dto.method.contains("GET"));
		assertFalse(dto.method.contains("OPTION"));
		assertNull(dto.consumingMimeType);
		assertEquals(1, dto.producingMimeType.length);
		assertEquals("text", dto.producingMimeType[0]);
		
		method = TestResource.class.getDeclaredMethod("helloWorld", new Class[0]);
		dto = DTOConverter.toResourceMethodInfoDTO(method);
		assertNull(dto);
		
	}

	/**
	 * Test method for {@link org.eclipselabs.osgi.jersey.dto.DTOConverter#checkMethodString(java.lang.reflect.Method, java.lang.Class, java.util.List)}.
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 */
	@Test
	public void testCheckMethodString() throws NoSuchMethodException, SecurityException {
		Class<TestResource> clazz = TestResource.class;
		assertEquals(3, clazz.getDeclaredMethods().length);
		int cnt = 0;
		for (Method m : clazz.getDeclaredMethods()) {
			String result = DTOConverter.getMethodStrings(m);
			switch (m.getName()) {
			case "helloWorld":
				assertNull(result);
				cnt++;
				break;
			case "postAndOut":
				assertTrue(result.contains("POST"));
				assertTrue(result.contains("PUT"));
				assertFalse(result.contains("DELETE"));
				assertFalse(result.contains("HEAD"));
				assertFalse(result.contains("GET"));
				assertFalse(result.contains("OPTION"));
				cnt++;
				break;
			case "postMe":
				assertEquals("POST", result);
				cnt++;
				break;
			default:
				fail("Not tested operation found in test stub TestResource");
				break;
			}
		}
		assertEquals(3, cnt);
	}

}
