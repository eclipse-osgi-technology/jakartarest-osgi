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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.osgi.annotation.bundle.Attribute;
import org.osgi.annotation.bundle.Capability;

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
@Capability(
		namespace = IMPLEMENTATION_NAMESPACE, 
		name = RequireRuntimeAdapter.JAKARTA_REST_JERSEY_ADAPTER,
		version = RequireRuntimeAdapter.JAKARTA_REST_JERSEY_ADAPTER_VERSION
)
public @interface ProvideRuntimeAdapter {
	
	@Attribute("provider")
	String value();

}