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
package org.eclipse.osgitech.rest.annotations;

import static org.osgi.namespace.implementation.ImplementationNamespace.IMPLEMENTATION_NAMESPACE;
import static org.osgi.namespace.service.ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE;
import static org.osgi.namespace.service.ServiceNamespace.SERVICE_NAMESPACE;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_IMPLEMENTATION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_SPECIFICATION_VERSION;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.osgi.annotation.bundle.Attribute;
import org.osgi.annotation.bundle.Capability;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.Providers;
import jakarta.ws.rs.sse.Sse;

/**
 * Require a Runtime Adapter bundle to host the whiteboard on an underlying servlet runtime,
 * 
 * e.g. A Jetty server, or the OSGi Servlet Whiteboard
 * 
 * @author Mark Hoffmann
 * @since 07.11.2022
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
		ElementType.TYPE, ElementType.PACKAGE
})
// Advertise the implementation capability for the whiteboard as per the specification
@Capability(
		namespace = IMPLEMENTATION_NAMESPACE, 
		version = JAKARTA_RS_WHITEBOARD_SPECIFICATION_VERSION, 
		name = JAKARTA_RS_WHITEBOARD_IMPLEMENTATION, 
		uses = {JakartarsWhiteboardConstants.class, WebApplicationException.class, 
				Client.class, ResourceInfo.class, Application.class, Providers.class, 
				Sse.class},
		attribute = { "provider=jersey", "jersey.version=3.0" }
)
// Advertise the service capability for the runtime as per the specification
@Capability(
		namespace = SERVICE_NAMESPACE,
		uses = {JakartarsServiceRuntime.class, ResourceDTO.class},
		attribute = CAPABILITY_OBJECTCLASS_ATTRIBUTE + "=org.osgi.service.jakartars.runtime.JakartarsServiceRuntime"
)
// Advertise the runtime adapter
@Capability(
		namespace = IMPLEMENTATION_NAMESPACE, 
		name = RequireRuntimeAdapter.JAKARTA_REST_JERSEY_ADAPTER,
		version = RequireRuntimeAdapter.JAKARTA_REST_JERSEY_ADAPTER_VERSION
)
public @interface ProvideRuntimeAdapter {
	
	@Attribute("provider")
	String value();

}