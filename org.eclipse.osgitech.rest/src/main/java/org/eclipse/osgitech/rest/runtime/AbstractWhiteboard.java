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
package org.eclipse.osgitech.rest.runtime;

import static org.eclipse.osgitech.rest.provider.JerseyConstants.JERSEY_WHITEBOARD_NAME;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_NAME;

import java.util.logging.Logger;

import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.eclipse.osgitech.rest.provider.application.JakartarsWhiteboardDispatcher;
import org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;

/**
 * A configurable component, that establishes a whiteboard
 * @author Mark Hoffmann
 * @since 11.10.2017
 */

public abstract class AbstractWhiteboard {

	Logger logger = Logger.getLogger(AbstractWhiteboard.class.getName());
	private volatile String name;
	

	protected final JakartarsWhiteboardDispatcher dispatcher= new JerseyWhiteboardDispatcher();
	
	protected volatile JakartarsWhiteboardProvider whiteboard;
	
	/**
	 * Updates the fields that are provided by service properties.
	 * @param ctx the component context
	 * @throws ConfigurationException thrown when no context is available or the expected property was not provided 
	 */
	protected void updateProperties(ComponentContext ctx) throws ConfigurationException {
		if (ctx == null) {
			throw new ConfigurationException(JAKARTA_RS_SERVICE_ENDPOINT, "No component context is availble to get properties from");
		}
		name = JerseyHelper.getPropertyWithDefault(ctx, JAKARTA_RS_NAME, null);
		if (name == null) {
			name = JerseyHelper.getPropertyWithDefault(ctx, JERSEY_WHITEBOARD_NAME, JERSEY_WHITEBOARD_NAME);
			if (name == null) {
				throw new ConfigurationException(JAKARTA_RS_NAME, "No name was defined for the whiteboard");
			}
		}
	}
	
	protected ServiceObjects<?> getServiceObjects(ServiceReference<?> reference) {
		return reference.getBundle().getBundleContext().getServiceObjects(reference);
	}
	
}
