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
package org.eclipse.osgitech.rest;

import java.util.logging.Logger;

import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsApplicationSelect;
import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsExtension;
import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsName;
import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsWhiteboardTarget;

@JakartarsExtension
@JakartarsName("MultiPartFeatureExtension")
@Component(name = "MultiPartFeatureComponent", property = {"multipart=true"})
@JakartarsApplicationSelect("(!(disableMultipart=true))")
@JakartarsWhiteboardTarget("(!(disableMultipart=true))")
public class MultiPartFeatureComponent implements Feature{
	
	private Logger logger = Logger.getLogger(MultiPartFeatureComponent.class.getName()); 

	@Override
	public boolean configure(FeatureContext context) {
		context.register(MultiPartFeature.class);
		logger.fine("Registering MultiPartFeature!");
		return true;
	}
}
