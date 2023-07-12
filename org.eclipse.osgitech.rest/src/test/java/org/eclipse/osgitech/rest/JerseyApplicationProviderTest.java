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
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.MessageBodyReader;

import org.eclipse.osgitech.rest.resources.TestApplication;
import org.eclipse.osgitech.rest.resources.TestExtension;
import org.eclipse.osgitech.rest.resources.TestLegacyApplication;
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
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * 
 * @author Mark Hoffmann
 * @since 21.09.2017
 */
@ExtendWith(MockitoExtension.class)
public class JerseyApplicationProviderTest {

	@Mock
	private ServiceObjects<Object> serviceObject;
	
	@Test
	public void testApplicationProviderNoRequiredProperties() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		// invalid filter schema
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		assertEquals("/", provider.getPath());
		assertNotNull(provider.getName());
		assertTrue(provider.getName().startsWith("."));
	}
	
	@Test
	public void testApplicationProviderWithRequiredPropertiesInvalidTargetFilter() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		BaseApplicationDTO  dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		assertEquals("/test", provider.getPath());
		assertEquals("./test", provider.getName());
	}
	
	@Test
	public void testApplicationProviderWithRequiredPropertiesInvalidTargetFilterButName() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "myTest");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		BaseApplicationDTO  dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		assertEquals("/test", provider.getPath());
		assertEquals("myTest", provider.getName());
	}
	
	@Test
	public void testApplicationProviderWithRequiredProperties() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla)");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertFalse(dto instanceof FailedApplicationDTO);
		
		assertEquals("/test", provider.getPath());
		assertEquals("./test", provider.getName());
	}
	
	@Test
	public void testApplicationProviderWithRequiredPropertiesAndName() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla)");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "myTest");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertFalse(dto instanceof FailedApplicationDTO);
		
		assertEquals("/test", provider.getPath());
		assertEquals("myTest", provider.getName());
	}
	
	@Test
	public void testHandleApplicationTargetFilterWrong() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		// invalid filter schema
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		Map<String, Object> runtimeProperties = new HashMap<>();
		assertFalse(provider.canHandleWhiteboard(runtimeProperties));
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		runtimeProperties.put("role", "rest");
		runtimeProperties.put("mandant", "eTest");
		
		assertFalse(provider.canHandleWhiteboard(runtimeProperties));
		
		dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
	}
	
	@Test
	public void testHandleApplicationTargetFilterNoMatch() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		// invalid filter schema
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla)");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		Map<String, Object> runtimeProperties = new HashMap<>();
		assertFalse(provider.canHandleWhiteboard(runtimeProperties));
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		runtimeProperties.put("role", "rest");
		runtimeProperties.put("mandant", "eTest");
		assertFalse(provider.canHandleWhiteboard(runtimeProperties));
		
		dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
	}
	
	@Test
	public void testHandleApplicationTargetFilterMatchButMissingBase() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		// invalid filter schema
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(|(role=bla)(mandant=eTest))");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		Map<String, Object> runtimeProperties = new HashMap<>();
		assertFalse(provider.canHandleWhiteboard(runtimeProperties));
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		runtimeProperties.put("role", "rest");
		runtimeProperties.put("mandant", "eTest");
		
		assertTrue(provider.canHandleWhiteboard(runtimeProperties));
		
		dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(&(role=rest)(mandant=eTest))");
		
		assertTrue(provider.canHandleWhiteboard(runtimeProperties));
		
		dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
	}
	
	@Test
	public void testHandleApplicationTargetFilterMatch() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		// invalid filter schema
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(|(role=bla)(mandant=eTest))");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		Map<String, Object> runtimeProperties = new HashMap<>();
		assertFalse(provider.canHandleWhiteboard(runtimeProperties));
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		runtimeProperties.put("role", "rest");
		runtimeProperties.put("mandant", "eTest");
		// now matches, despite being failed
		assertTrue(provider.canHandleWhiteboard(runtimeProperties));
		
		dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(&(role=rest)(mandant=eTest))");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		
		provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		assertTrue(provider.canHandleWhiteboard(runtimeProperties));
		
		dto = provider.getApplicationDTO();
		assertFalse(dto instanceof FailedApplicationDTO);
	}
	
	@Test
	public void testHandleApplicationNullProperties() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		// invalid filter schema
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(|(role=bla)(mandant=eTest))");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		
		assertFalse(provider.canHandleWhiteboard(null));
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		assertFalse(provider.canHandleWhiteboard(null));
		
		dto = provider.getApplicationDTO();
		assertFalse(dto instanceof FailedApplicationDTO);
	}
	
	@Test
	public void testLegacyApplicationWithNullProperties() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		// invalid filter schema
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(|(role=bla)(mandant=eTest))");
		
		JerseyApplicationProvider provider = new JerseyApplicationProvider(new TestLegacyApplication(), applicationProperties);
		
		assertFalse(provider.canHandleWhiteboard(null));
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertTrue(dto instanceof FailedApplicationDTO);
		FailedApplicationDTO failedDto = (FailedApplicationDTO) dto;
		assertEquals(DTOConstants.FAILURE_REASON_VALIDATION_FAILED, failedDto.failureReason);
		
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		provider = new JerseyApplicationProvider(new Application(), applicationProperties);
		assertFalse(provider.canHandleWhiteboard(null));
		
		dto = provider.getApplicationDTO();
		
		// according to the current spec an application that does not match a whiteboard will not produce a failure dto 
		assertFalse(dto instanceof FailedApplicationDTO);
	}
	
//	@Test
//	public void testLegacyApplicationChangeInvalid() {
//		Map<String, Object> applicationProperties = new HashMap<>();
//		applicationProperties.put("something", "else");
//		// invalid filter schema
//		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(|(role=bla)(mandant=eTest))");
//		
//		JerseyApplicationProvider provider = new JerseyApplicationProvider(new TestLegacyApplication(), applicationProperties);
//		
//		assertTrue(provider.isChanged());
//		
//		when(serviceObject.getService()).thenReturn(new TestResource());
//		Map<String, Object> contentProperties = new HashMap<>();
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
//		JakartarsResourceProvider resource = new JerseyResourceProvider<Object>(serviceObject, contentProperties);
//		
//		assertFalse(provider.addResource(resource));
//		assertTrue(provider.isChanged());
//		assertFalse(provider.removeResource(resource));
//		assertTrue(provider.isChanged());
//		
//		when(serviceObject.getService()).thenReturn(new TestExtension());
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "true");
//		contentProperties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
//		JakartarsExtensionProvider extension = new JerseyExtensionProvider<Object>(serviceObject, contentProperties);
//		
//		assertFalse(provider.addExtension(extension));
//		assertTrue(provider.isChanged());
//		assertFalse(provider.removeExtension(extension));
//		assertTrue(provider.isChanged());
//	}
//	
//	@Test
//	public void testLegacyApplicationChange() {
//		Map<String, Object> applicationProperties = new HashMap<>();
//		applicationProperties.put("something", "else");
//		// invalid filter schema
//		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
//		
//		JerseyApplicationProvider provider = new JerseyApplicationProvider(new TestApplication(), applicationProperties);
//		
//		assertTrue(provider.isEmpty());
//		assertTrue(provider.isChanged());
//		provider.markUnchanged();
//		
//		provider = new JerseyApplicationProvider(new TestLegacyApplication(), applicationProperties);
//		
//		assertFalse(provider.isEmpty());
//		assertTrue(provider.isChanged());
//		provider.markUnchanged();
//		
//		when(serviceObject.getService()).thenReturn(new TestResource());
//		Map<String, Object> contentProperties = new HashMap<>();
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
//		JakartarsResourceProvider resource = new JerseyResourceProvider<Object>(serviceObject, contentProperties);
//		
////		assertFalse(provider.addResource(resource));
//		assertFalse(resource.canHandleApplication(provider));
//		assertFalse(provider.isChanged());
//		assertFalse(provider.removeResource(resource));
//		assertFalse(provider.isChanged());
//		
//		when(serviceObject.getService()).thenReturn(new TestExtension());
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "true");
//		contentProperties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
//		JakartarsExtensionProvider extension = new JerseyExtensionProvider<Object>(serviceObject, contentProperties);
//		
////		assertFalse(provider.addExtension(extension));
//		assertFalse(extension.canHandleApplication(provider));
//		assertFalse(provider.isChanged());
//		assertFalse(provider.removeExtension(extension));
//		assertFalse(provider.isChanged());
//	}
//	
//	@Test
//	public void testApplicationChangeInvalid() {
//		Map<String, Object> applicationProperties = new HashMap<>();
//		applicationProperties.put("something", "else");
//		// invalid filter schema
//		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(|(role=bla)(mandant=eTest))");
//		
//		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
//		
//		assertTrue(provider.isChanged());
//		provider.markUnchanged();
//		
//		when(serviceObject.getService()).thenReturn(new TestResource());
//		Map<String, Object> contentProperties = new HashMap<>();
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
//		JakartarsResourceProvider resource = new JerseyResourceProvider<Object>(serviceObject, contentProperties);
//		
//		assertFalse(provider.addResource(resource));
//		assertFalse(provider.isChanged());
//		assertFalse(provider.removeResource(resource));
//		assertFalse(provider.isChanged());
//		
//		when(serviceObject.getService()).thenReturn(new TestExtension());
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "true");
//		contentProperties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
//		JakartarsExtensionProvider extension = new JerseyExtensionProvider<Object>(serviceObject, contentProperties);
//		
//		assertFalse(provider.addExtension(extension));
//		assertFalse(provider.isChanged());
//		assertFalse(provider.removeExtension(extension));
//		assertFalse(provider.isChanged());
//	}
//	
//	@Test
//	public void testApplicationNoChange() {
//		Map<String, Object> applicationProperties = new HashMap<>();
//		applicationProperties.put("something", "else");
//		// invalid filter schema
//		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
//		
//		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
//		
//		assertTrue(provider.isChanged());
//		provider.markUnchanged();
//		
//		when(serviceObject.getService()).thenReturn(new TestResource());
//		Map<String, Object> contentProperties = new HashMap<>();
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
//		JakartarsResourceProvider resource = new JerseyResourceProvider<Object>(serviceObject, contentProperties);
//		
//		assertFalse(resource.canHandleApplication(provider));
////		assertFalse(provider.addResource(resource));
//		assertFalse(provider.isChanged());
//		assertFalse(provider.removeResource(resource));
//		assertFalse(provider.isChanged());
//		
//		when(serviceObject.getService()).thenReturn(new TestExtension());
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "true");
//		contentProperties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
//		JakartarsExtensionProvider extension = new JerseyExtensionProvider<Object>(serviceObject, contentProperties);
//		
//		assertFalse(extension.canHandleApplication(provider));
////		assertFalse(provider.addExtension(extension));
//		assertFalse(provider.isChanged());
//		assertFalse(provider.removeExtension(extension));
//		assertFalse(provider.isChanged());
//	}
//	
//	@Test
//	public void testApplicationChange() {
//		Map<String, Object> applicationProperties = new HashMap<>();
//		applicationProperties.put("name", "me");
//		// invalid filter schema
//		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
//		
//		JerseyApplicationProvider provider = new JerseyApplicationProvider(new Application(), applicationProperties);
//		
//		assertTrue(provider.isChanged());
//		provider.markUnchanged();
//		
//		when(serviceObject.getService()).thenReturn(new TestResource());
//		Map<String, Object> contentProperties = new HashMap<>();
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SELECT, "(name=me)");
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "res_one");
//		JakartarsResourceProvider resource = new JerseyResourceProvider<Object>(serviceObject, contentProperties);
//		
//		Map<String, Object> contentProperties2 = new HashMap<>(contentProperties);
//		contentProperties2.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "two");
//		JakartarsResourceProvider resource2 = new JerseyResourceProvider<Object>(serviceObject, contentProperties2);
//		
//		assertTrue(provider.addResource(resource));
//		assertTrue(provider.isChanged());
//		
//		provider.markUnchanged();
//		
//		assertFalse(provider.isChanged());
//		
//		assertFalse(provider.removeResource(resource2));
//		assertFalse(provider.isChanged());
//		assertTrue(provider.removeResource(resource));
//		assertTrue(provider.isChanged());
//		
//		when(serviceObject.getService()).thenReturn(new TestExtension());
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "true");
//		contentProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "ext_one");
//		
////		This should advertise one of the valid extension types to be considered an extension
////		contentProperties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});		
//		contentProperties.put(Constants.OBJECTCLASS, new String[] {MessageBodyReader.class.getName()});		
//		
//		JakartarsExtensionProvider extension = new JerseyExtensionProvider<Object>(serviceObject, contentProperties);
//		
//		assertTrue(provider.addExtension(extension));
//		assertTrue(provider.isChanged());
//		provider.markUnchanged();
//		assertFalse(provider.isChanged());
//		assertTrue(provider.removeExtension(extension));
//		assertTrue(provider.isChanged());
//	}
}
