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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.ext.MessageBodyReader;

import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider;
import org.eclipse.osgitech.rest.resources.TestApplication;
import org.eclipse.osgitech.rest.resources.TestExtension;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyExtensionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceObjects;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.FailedExtensionDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * Test the extension provider. Because it is extended from the resource provider, only the specific behavior is tested.
 * For all other Tests look at {@link JakartarsResourceProviderTest}
 * @author Mark Hoffmann
 * @since 21.09.2017
 */
@ExtendWith(MockitoExtension.class)
public class JakartarsExtensionProviderTest {

	@Mock
	private ServiceObjects<Object> serviceObject;
	
	/**
	 * Test extension specific behavior 
	 */
	@Test
	public void testExtension() {
		Map<String, Object> applicationProperties = new HashMap<>();
		applicationProperties.put("something", "else");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET, "(hallo=bla)");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, "test");
		applicationProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_NAME, "test");
		
		JakartarsApplicationProvider provider = new JerseyApplicationProvider(new TestApplication(), applicationProperties);
		
		BaseApplicationDTO dto = provider.getApplicationDTO();
		assertFalse(dto instanceof FailedApplicationDTO);
		
		assertEquals("/test", provider.getPath());
		assertEquals("test", provider.getName());
		
		Map<String, Object> resourceProperties = new HashMap<>();
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, "true");
		resourceProperties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
		when(serviceObject.getService()).thenReturn(new TestExtension());
		JakartarsExtensionProvider resourceProvider = new JerseyExtensionProvider<Object>(serviceObject, resourceProperties);
		
		BaseExtensionDTO resourceDto = resourceProvider.getExtensionDTO();
		assertTrue(resourceDto instanceof FailedExtensionDTO);
		assertFalse(resourceProvider.isExtension());
		assertTrue(resourceProvider.isSingleton());
		
		resourceProperties.clear();
		resourceProperties.put(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION, "true");
		
//		This should advertise one of the valid extension types to be considered an extension
//		resourceProperties.put(Constants.OBJECTCLASS, new String[] {TestExtension.class.getName()});
		resourceProperties.put(Constants.OBJECTCLASS, new String[] {MessageBodyReader.class.getName()});
		resourceProvider = new JerseyExtensionProvider<Object>(serviceObject, resourceProperties);
		
		resourceDto = resourceProvider.getExtensionDTO();
		assertFalse(resourceDto instanceof FailedExtensionDTO);
		assertTrue(resourceProvider.isExtension());
		assertTrue(resourceProvider.isSingleton());
		
	}
	
}
