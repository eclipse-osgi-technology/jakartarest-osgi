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
package org.eclipse.osgitech.rest.client.check;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.condition.Condition;

import aQute.bnd.annotation.service.ServiceCapability;

/**
 * Component to check the Jersey client readiness and raises a condition
 * @author Mark Hoffmann
 * @since 28.01.2025
 */
@Component(immediate = true)
@ServiceCapability(value = Condition.class)
public class JerseyClientRuntimeCheck {
	
	private JerseyClientBundleTracker jerseyTracker;
	
	@Activate
	public void activate(BundleContext ctx) {
		jerseyTracker = new JerseyClientBundleTracker(ctx);
		jerseyTracker.open();
		
	}
	
	@Deactivate
	public void deactivate() {
		jerseyTracker.close();
	}

}
