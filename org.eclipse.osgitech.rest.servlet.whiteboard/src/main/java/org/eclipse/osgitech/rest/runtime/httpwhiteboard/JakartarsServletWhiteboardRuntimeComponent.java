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
package org.eclipse.osgitech.rest.runtime.httpwhiteboard;

import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.provider.JerseyConstants;
import org.eclipse.osgitech.rest.runtime.AbstractWhiteboard;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
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
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.condition.Condition;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.ws.rs.core.Application;

/**
 * This component handles the lifecycle of a {@link JakartarsServiceRuntime}
 * @author Mark Hoffmann
 * @since 30.07.2017
 */
@Component(name="JakartarsServletWhiteboardRuntimeComponent", 
immediate=true, configurationPolicy=ConfigurationPolicy.REQUIRE, 
	reference = @Reference(name = "runtimeCondition", 
	service = Condition.class,
	target = JerseyConstants.JERSEY_RUNTIME_CONDITION))
public class JakartarsServletWhiteboardRuntimeComponent extends AbstractWhiteboard {

	private static Logger logger = Logger.getLogger(JakartarsServletWhiteboardRuntimeComponent.class.getName());
	private ServiceTracker<HttpServiceRuntime, HttpServiceRuntime> httpRuntimeTracker;
	private ComponentContext componentContext;

	/**
	 * Called on component activation
	 * @param componentContext the component context
	 * @throws ConfigurationException 
	 */
	@Activate
	public void activate(final ComponentContext componentContext) throws ConfigurationException {
		this.componentContext = componentContext;
		updateProperties(componentContext);
		if (whiteboard != null) {
			whiteboard.teardown();
		}
		whiteboard = new ServletWhiteboardBasedJerseyServiceRuntime();
		whiteboard.initialize(componentContext);
		dispatcher.setWhiteboardProvider(whiteboard);
		String target = (String) componentContext.getProperties().get(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET);
		if (target != null) {
			try {
				target = String.format("(&(objectClass=org.osgi.service.servlet.runtime.HttpServiceRuntime)%s)", target);
				Filter f = FrameworkUtil.createFilter(target);
				httpRuntimeTracker = new ServiceTracker<HttpServiceRuntime, HttpServiceRuntime>(componentContext.getBundleContext(), f, customizer);
			} catch (InvalidSyntaxException e) {
				throw new ConfigurationException(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "Invalid target defined: " + target, e);
			}
		}
		httpRuntimeTracker.open();
	}

	/**
	 * Called on component modification
	 * @param context the component context
	 * @throws ConfigurationException 
	 */
	@Modified
	public void modified(ComponentContext context) throws ConfigurationException {
		componentContext = context;
		doUpdate();
	}

	/**
	 * Called on component de-activation
	 * @param context the component context
	 */
	@Deactivate
	public void deactivate(ComponentContext context) {
		httpRuntimeTracker.close();
		doShutdown();
	}

	/**
	 * Adds a new application
	 * @param application the application to add
	 * @param properties the service properties
	 */
	@Reference(name="application", service=Application.class,cardinality=ReferenceCardinality.MULTIPLE, policy=ReferencePolicy.DYNAMIC, unbind="unbindApplication", updated = "updatedApplication", target="(" + JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE	+ "=*)")
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

	@Reference(service = AnyService.class, target = "(" + JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION	+ "=true)", cardinality = MULTIPLE, policy = DYNAMIC)
	public void bindJakartarsExtension(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {
		updatedJakartarsExtension(jakartarsExtensionSR,properties);
	}

	public void updatedJakartarsExtension(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {
		logger.fine("Handle extension " + jakartarsExtensionSR + " properties: " + properties);
		ServiceObjects<?> so = getServiceObjects(jakartarsExtensionSR);
		dispatcher.addExtension(so, properties);

	}
	public void unbindJakartarsExtension(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {
		dispatcher.removeExtension(properties);
	}

	@Reference(service = AnyService.class, target = "(" + JAKARTA_RS_RESOURCE + "=true)", cardinality = MULTIPLE, policy = DYNAMIC)
	public void bindJakartarsResource(ServiceReference<Object> jakartarsExtensionSR, Map<String, Object> properties) {
		updatedJakartarsResource(jakartarsExtensionSR,properties);
	}

	public void updatedJakartarsResource(ServiceReference<Object> jakartarsResourceSR, Map<String, Object> properties) {
		logger.fine("Handle resource " + jakartarsResourceSR + " properties: " + properties);
		ServiceObjects<?> so = getServiceObjects(jakartarsResourceSR);
		dispatcher.addResource(so, properties);

	}
	public void unbindJakartarsResource(ServiceReference<Object> jakartarsResourceSR, Map<String, Object> properties) {
		dispatcher.removeResource(properties);
	}

	/**
	 * Updates the whiteboard and dispatcher
	 * @throws ConfigurationException
	 */
	private void doUpdate() throws ConfigurationException {
		updateProperties(componentContext);
		whiteboard.modified(componentContext);
		dispatcher.dispatch();
	}

	/**
	 * Shuts everything down gracefully
	 */
	private void doShutdown() {
		if (dispatcher != null) {
			dispatcher.deactivate();
		}
		if (whiteboard != null) {
			whiteboard.teardown();
			whiteboard = null;
		}
	}

	private ServiceTrackerCustomizer<HttpServiceRuntime, HttpServiceRuntime> customizer = new ServiceTrackerCustomizer<HttpServiceRuntime, HttpServiceRuntime>() {

		@Override
		public HttpServiceRuntime addingService(ServiceReference<HttpServiceRuntime> reference) {
			HttpServiceRuntime service = componentContext.getBundleContext().getService(reference);
			if (service != null) {
				dispatcher.dispatch();
				whiteboard.startup();
				return service;
			}
			return null;
		}

		@Override
		public void modifiedService(ServiceReference<HttpServiceRuntime> reference,
				HttpServiceRuntime service) {
			try {
				doUpdate();
			} catch (ConfigurationException e) {
				logger.log(Level.SEVERE, "Error while updating Http Servlet Whiteboards, shutting down", e);
				doShutdown();
			}
		}

		@Override
		public void removedService(ServiceReference<HttpServiceRuntime> reference, HttpServiceRuntime service) {
			doShutdown();
		}
	};
}
