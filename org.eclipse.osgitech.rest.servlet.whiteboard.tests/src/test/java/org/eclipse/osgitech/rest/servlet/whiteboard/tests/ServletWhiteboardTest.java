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
package org.eclipse.osgitech.rest.servlet.whiteboard.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

/**
 * Test running whiteboard implementation for the Servlet Whiteboard specification
 * @author Mark Hoffmann
 * @since 09.11.2022
 */
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
public class ServletWhiteboardTest {
	
	private BundleContext context;
	
	@BeforeEach
	public void beforeEach(@InjectBundleContext BundleContext context) {
		this.context = context;
	}
	
	@Test
	public void testWhiteboard(@InjectService(cardinality = 0) ServiceAware<JakartarsServiceRuntime> whiteboardAware) {
		
		try {
			assertNotNull(whiteboardAware.waitForService(1000l));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail("Interrupted");
		}
		
		Dictionary<String,Object> properties = new Hashtable<>();
		properties.put(JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE, Boolean.TRUE);

		ServiceRegistration<?> registration = context.registerService(WhiteboardResource.class, new WhiteboardResource(), properties);

		try {
			Thread.sleep(1000l);
			
			String baseURI = getBaseURI(whiteboardAware);
			HttpClient httpClient = HttpClient.newBuilder()
		            .version(HttpClient.Version.HTTP_1_1)
		            .connectTimeout(Duration.ofSeconds(10))
		            .build();
			String uri = baseURI + "whiteboard/resource";
	        HttpRequest request = HttpRequest.newBuilder()
	                .GET()
	                .uri(URI.create(uri))
	                .build();

	        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode());
			assertEquals("Hello World", response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail("Interrupted");
		} catch (MalformedURLException e) {
			fail("Malformed URL");
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			fail("IOException");
		}
		
		registration.unregister();
	}
	
	protected String getBaseURI(ServiceAware<JakartarsServiceRuntime> whiteboardAware) {
		ServiceReference<JakartarsServiceRuntime> runtime = whiteboardAware.getServiceReference();
		Object value = runtime.getProperty(JAKARTA_RS_SERVICE_ENDPOINT);

		if (value instanceof String) {
			return (String) value;
		} else if (value instanceof String[]) {
			String[] values = (String[]) value;
			if (values.length > 0) {
				return values[values.length -1];
			}
		} else if (value instanceof Collection) {
			if (!((Collection<?>)value).isEmpty()) { 
				return String.valueOf(((Collection< ? >) value).iterator().next());
			}
		}

		throw new IllegalArgumentException(
				"The JAXRS Service Runtime did not declare an endpoint property");
	}

}
