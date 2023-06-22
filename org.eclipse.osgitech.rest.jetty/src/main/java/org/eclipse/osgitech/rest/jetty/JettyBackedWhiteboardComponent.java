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
package org.eclipse.osgitech.rest.jetty;

import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE;

import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.runtime.AbstractWhiteboard;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.AnyService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.core.Application;

/**
 * A configurable component, that establishes a whiteboard
 * @author Mark Hoffmann
 * @since 11.10.2017
 */

@Component(name = "JakartarsWhiteboardComponent", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class JettyBackedWhiteboardComponent extends AbstractWhiteboard {

	Logger logger = Logger.getLogger(JettyBackedWhiteboardComponent.class.getName());

	/**
	 * Called on component activation
	 * @param componentContext the component context
	 * @throws ConfigurationException 
	 */
	@Activate
	public void activate(ComponentContext componentContext) throws ConfigurationException {
		updateProperties(componentContext);
		
		if (whiteboard != null) {
			whiteboard.teardown();
		}
		whiteboard = new JerseyServiceRuntime();
		// activate and start server
		whiteboard.initialize(componentContext);
//		dispatcher.setBatchMode(true);
		dispatcher.setWhiteboardProvider(whiteboard);
		dispatcher.dispatch();
		whiteboard.startup();
	}

	/**
	 * Called on component modification
	 * @param context the component context
	 * @throws ConfigurationException 
	 */
	@Modified
	public void modified(ComponentContext context) throws ConfigurationException {
		updateProperties(context);
		dispatcher.dispatch();
		whiteboard.modified(context);
	}

	/**
	 * Called on component de-activation
	 * @param context the component context
	 */
	@Deactivate
	public void deactivate(ComponentContext context) {
		if (dispatcher != null) {
			dispatcher.deactivate();
		}
		if (whiteboard != null) {
			whiteboard.teardown();
			whiteboard = null;
		}
	}
	
	/**
	 * Adds a new application
	 * @param application the application to add
	 * @param properties the service properties
	 */
	@Reference(service = Application.class, cardinality = MULTIPLE, policy = DYNAMIC, target = "(" + JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE + "=*)")
	public void bindApplication(Application application, Map<String, Object> properties) {
		dispatcher.addApplication(application, properties);
	
	}
	
	/**
	 * Adds a new application
	 * @param application the application to add
	 * @param properties the service properties
	 */
	public void updatedApplication(Application application, Map<String, Object> properties) {
		dispatcher.removeApplication(application, properties);
		dispatcher.addApplication(application, properties);
	}
	
	/**
	 * Removes a application 
	 * @param application the application to remove
	 * @param properties the service properties
	 */
	public void unbindApplication(Application application, Map<String, Object> properties) {
		dispatcher.removeApplication(application, properties);
	}

	@Reference(service = AnyService.class, target = "(" + JAKARTA_RS_EXTENSION
			+ "=true)", cardinality = MULTIPLE, policy = DYNAMIC)
	public void bindJakartarsExtension(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {

		updatedJakartarsExtension(jakartarsExtensionSR, properties);
	}

	public void updatedJakartarsExtension(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {
		logger.fine("Handle extension " + jakartarsExtensionSR + " properties: " + properties);
		ServiceObjects<?> so = getServiceObjects(jakartarsExtensionSR);
		dispatcher.addExtension(so, properties);

	}

	public void unbindJakartarsExtension(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {
		dispatcher.removeExtension(properties);
	}

	@Reference(service = AnyService.class, target = "(" + JAKARTA_RS_RESOURCE
			+ "=true)", cardinality = MULTIPLE, policy = DYNAMIC)
	public void bindJakartarsResource(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {
		updatedJakartarsResource(jakartarsExtensionSR, properties);
	}

	public void updatedJakartarsResource(ServiceReference<Object> jakartarsResourceSR, Map<String, Object> properties) {
		logger.fine("Handle resource " + jakartarsResourceSR + " properties: " + properties);
		ServiceObjects<?> so = getServiceObjects(jakartarsResourceSR);
		dispatcher.addResource(so, properties);

	}

	public void unbindJakartarsResource(ServiceReference<Object> jakartarsResourceSR, Map<String, Object> properties) {
		dispatcher.removeResource(properties);
	}
}
