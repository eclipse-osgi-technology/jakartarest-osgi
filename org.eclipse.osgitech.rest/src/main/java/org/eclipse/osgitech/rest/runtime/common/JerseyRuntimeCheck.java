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
package org.eclipse.osgitech.rest.runtime.common;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.condition.Condition;

import aQute.bnd.annotation.service.ServiceCapability;

/**
 * Component to check the Jersey readiness and raises a condition
 * @author mark
 * @since 12.07.2022
 */
@Component(immediate = true)
@ServiceCapability(value = Condition.class)
public class JerseyRuntimeCheck {
	
	private JerseyBundleTracker jerseyTracker;
	
	@Activate
	public void activate(BundleContext ctx) {
		jerseyTracker = new JerseyBundleTracker(ctx);
		jerseyTracker.open();
		
	}
	
	@Deactivate
	public void deactivate() {
		jerseyTracker.close();
	}

}
