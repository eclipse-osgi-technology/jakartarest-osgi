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
package org.eclipse.osgitech.rest.tests.subresource;

import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsResource;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * A parent resource DS component that uses a sub-resource locator to return SubResource instances.
 * The SubResource is obtained via ComponentServiceObjects, which means SCR manages the lifecycle.
 * When this component is deactivated, SCR should also release any SubResource instances obtained
 * through the ComponentServiceObjects.
 */
@JakartarsResource
@Component(scope = ServiceScope.PROTOTYPE, service = ParentResource.class)
@Path("/parent/{name}")
public class ParentResource {

	private static final String NAME = "ParentResource";

	@Reference
	private LifecycleTracker tracker;

	@Reference
	private ComponentServiceObjects<SubResource> subResourceServiceObjects;

	@Activate
	void activate() {
		tracker.recordActivate(NAME);
	}

	@Deactivate
	void deactivate() {
		tracker.recordDeactivate(NAME);
	}

	/**
	 * Sub-resource locator method. Returns a SubResource instance obtained from OSGi via SCR.
	 * Note: No HTTP method annotation (@GET, @POST, etc.) - this is a locator.
	 *
	 * @param name the path parameter from the parent path
	 * @return SubResource instance to handle the remaining path
	 */
	@Path("sub")
	public SubResource getSubResource(@PathParam("name") String name) {
		return subResourceServiceObjects.getService();
	}
}
