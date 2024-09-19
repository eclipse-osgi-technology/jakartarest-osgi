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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.eclipse.osgitech.rest.provider.JerseyConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.condition.Condition;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

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
public class JakartarsServletWhiteboardRuntimeComponent {

	private BundleContext context;
	private String target;
	private String basePath;
	private ServiceTracker<HttpServiceRuntime, ServletWhiteboardBasedJerseyServiceRuntime> httpRuntimeTracker;
	private Map<String, Object> props;

	/**
	 * Called on component activation
	 * @param componentContext the component context
	 * @throws ConfigurationException 
	 */
	@Activate
	public void activate(BundleContext context, Map<String, Object> props) throws ConfigurationException {
		
		this.context = context;
		this.props = props;
		target = (String) props.get(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET);
		basePath = (String) props.getOrDefault(JerseyConstants.JERSEY_CONTEXT_PATH, "/");
		openTracker();
	}

	private void openTracker() throws ConfigurationException {
		Filter f;
		try {
			if (target != null) {
				target = String.format("(&(objectClass=org.osgi.service.servlet.runtime.HttpServiceRuntime)%s)", target);
			} else {
				target = "(objectClass=org.osgi.service.servlet.runtime.HttpServiceRuntime)";
			}
			f = context.createFilter(target);
		} catch (InvalidSyntaxException e) {
				throw new ConfigurationException(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "Invalid target defined: " + target, e);
		}
		httpRuntimeTracker = new HttpServiceTracker(context, f, null);
		httpRuntimeTracker.open();
	}

	/**
	 * Called on component modification
	 * @param context the component context
	 * @throws ConfigurationException 
	 */
	@Modified
	public void modified(Map<String, Object> props) throws ConfigurationException {
		String oldTarget = target;
		String oldBase = basePath;
		target = (String) props.get(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET);
		basePath = (String) props.getOrDefault(JerseyConstants.JERSEY_CONTEXT_PATH, "/");
		
		if(!Objects.equals(oldTarget, target) || !Objects.equals(oldBase, basePath)) {
			httpRuntimeTracker.close();
			openTracker();
		}
	}

	/**
	 * Called on component de-activation
	 * @param context the component context
	 */
	@Deactivate
	public void deactivate(ComponentContext context) {
		httpRuntimeTracker.close();
	}


	private final class HttpServiceTracker
			extends ServiceTracker<HttpServiceRuntime, ServletWhiteboardBasedJerseyServiceRuntime> {
		private HttpServiceTracker(BundleContext context, Filter filter,
				ServiceTrackerCustomizer<HttpServiceRuntime, ServletWhiteboardBasedJerseyServiceRuntime> customizer) {
			super(context, filter, customizer);
		}
	
		@Override
		public ServletWhiteboardBasedJerseyServiceRuntime addingService(ServiceReference<HttpServiceRuntime> reference) {
			return new ServletWhiteboardBasedJerseyServiceRuntime(context, basePath, reference, props);
		}
	
		@Override
		public void removedService(ServiceReference<HttpServiceRuntime> reference, ServletWhiteboardBasedJerseyServiceRuntime runtime) {
			runtime.teardown(5, TimeUnit.SECONDS);
		}
	}
}
