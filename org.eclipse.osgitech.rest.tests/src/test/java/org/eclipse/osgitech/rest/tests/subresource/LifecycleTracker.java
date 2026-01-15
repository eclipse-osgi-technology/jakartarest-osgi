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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Component;

/**
 * Singleton service that tracks component lifecycle events (activate/deactivate).
 * This service is registered by DS and used by other DS components
 * to record their lifecycle events.
 */
@Component(service = LifecycleTracker.class)
public class LifecycleTracker {

	private final List<String> events = new CopyOnWriteArrayList<>();

	/**
	 * Records a component activation event.
	 * @param componentName the name of the component that was activated
	 */
	public void recordActivate(String componentName) {
		events.add("ACTIVATE:" + componentName);
	}

	/**
	 * Records a component deactivation event.
	 * @param componentName the name of the component that was deactivated
	 */
	public void recordDeactivate(String componentName) {
		events.add("DEACTIVATE:" + componentName);
	}

	/**
	 * Returns a copy of all recorded events in order.
	 * @return list of events in format "ACTIVATE:ComponentName" or "DEACTIVATE:ComponentName"
	 */
	public List<String> getEvents() {
		return new ArrayList<>(events);
	}

	/**
	 * Clears all recorded events.
	 */
	public void clear() {
		events.clear();
	}
}
