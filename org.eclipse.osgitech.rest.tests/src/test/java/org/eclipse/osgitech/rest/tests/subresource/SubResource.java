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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * A sub-resource DS component that is returned by ParentResource's sub-resource locator.
 * This component has prototype scope and tracks its lifecycle via LifecycleTracker.
 * SCR should call ungetService() when the parent resource is released.
 */
@Component(scope = ServiceScope.PROTOTYPE, service = SubResource.class)
public class SubResource {

	private static final String NAME = "SubResource";

	@Reference
	private LifecycleTracker tracker;

	@Activate
	void activate() {
		tracker.recordActivate(NAME);
	}

	@Deactivate
	void deactivate() {
		tracker.recordDeactivate(NAME);
	}

	@GET
	@Path("/data")
	@Produces(MediaType.TEXT_PLAIN)
	public String getData() {
		return "sub-resource-data";
	}
}
