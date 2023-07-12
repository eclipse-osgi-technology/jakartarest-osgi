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

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_ID;
import static org.osgi.framework.FrameworkUtil.asMap;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_DEFAULT_APPLICATION;
import static org.osgi.service.servlet.runtime.HttpServiceRuntimeConstants.HTTP_SERVICE_ENDPOINT;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_DEFAULT_CONTEXT_NAME;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.annotations.ProvideRuntimeAdapter;
import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.eclipse.osgitech.rest.runtime.JerseyServiceRuntime;
import org.eclipse.osgitech.rest.runtime.WhiteboardServletContainer;
import org.glassfish.jersey.server.ResourceConfig;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.service.servlet.whiteboard.annotations.RequireHttpWhiteboard;

import jakarta.servlet.Servlet;

/**
 * Implementation of the {@link JakartarsServiceRuntime} for a Jersey implementation
 * @author Mark Hoffmann
 * @since 12.07.2017
 */
@ProvideRuntimeAdapter(HttpWhiteboardConstants.HTTP_WHITEBOARD_IMPLEMENTATION)
@RequireHttpWhiteboard
public class ServletWhiteboardBasedJerseyServiceRuntime {

	private final Logger logger = Logger.getLogger(ServletWhiteboardBasedJerseyServiceRuntime.class.getName());
	private final BundleContext context;
	private final String basePath;
	private final ServiceReference<HttpServiceRuntime> runtimeTarget;
	private final Long httpId;
	private final String httpWhiteboardTarget;
	private final JerseyServiceRuntime<WhiteboardServletContainer> runtime;

	private final Map<String, RestContext> pathsToServlets = new HashMap<>();
	
	private static class RestContext {
		private final ServiceRegistration<ServletContextHelper> contextHelperReg;
		private final ServiceRegistration<Servlet> servletReg;
		private final WhiteboardServletContainer servlet;
		
		public RestContext(ServiceRegistration<ServletContextHelper> contextHelperReg,
				ServiceRegistration<Servlet> servletReg, WhiteboardServletContainer servlet) {
			this.contextHelperReg = contextHelperReg;
			this.servletReg = servletReg;
			this.servlet = servlet;
		}
	}

	public ServletWhiteboardBasedJerseyServiceRuntime(BundleContext context, String basePath,
			ServiceReference<HttpServiceRuntime> runtimeTarget) {
		this.context = context;
		this.basePath = basePath;
		this.runtimeTarget = runtimeTarget;
		httpId = (Long) runtimeTarget.getProperty(SERVICE_ID);
		this.httpWhiteboardTarget = String.format("(%s=%s)", SERVICE_ID, httpId);
		this.runtime = new JerseyServiceRuntime<>(context, this::registerContainer, this::unregisterContainer);
		
		runtime.start(Map.of(JAKARTA_RS_SERVICE_ENDPOINT, getURLs(), 
				SERVICE_DESCRIPTION, "REST whiteboard for HttpServiceRuntime " + httpId));
	}


	/* 
	 * (non-Javadoc)
	 * @see org.eclipselabs.osgi.jersey.runtime.JakartarsJerseyHandler#getURLs(org.osgi.service.component.ComponentContext)
	 */
	private String[] getURLs() {
		//first look which http whiteboards would fit
		String[] endpoints = JerseyHelper.getStringPlusProperty(HTTP_SERVICE_ENDPOINT, asMap(runtimeTarget.getProperties()));
		
		return Arrays.stream(endpoints)
				.sorted(this::preferIPv4)
				.map(s -> buildEndPoint(s, basePath))
				.toArray(String[]::new);
	}
	

	private int preferIPv4(String a, String b) {
		if(a == null) {
			return b == null ? 0 : 1;
		} else if (b == null) {
			return -1;
		}
		int aIdx = a.indexOf("://");
		int bIdx = b.indexOf("://");
		
		boolean aIPv6 = a.charAt(aIdx < 0 ? 0 : aIdx + 3) == '[';
		boolean bIPv6 = b.charAt(bIdx < 0 ? 0 : bIdx + 3) == '[';
		
		if(aIPv6 && !bIPv6) {
			return 1;
		} else if (!aIPv6 && bIPv6) {
			return -1;
		} else {
			return a.compareTo(b);
		}
	}


	private String buildEndPoint(String endpoint, String path) {
		String rsEndpoint = endpoint;
		if(!endpoint.endsWith("/")) {
			rsEndpoint += "/";
		}
		if (basePath.startsWith("/")) {
			rsEndpoint += basePath.substring(1);
		} else {
			rsEndpoint += basePath;
		}
		if (!rsEndpoint.endsWith("/")) {
			rsEndpoint += "/";
		}
		if(path != null && path.startsWith("/")) {
			rsEndpoint += path.substring(1); 
		}
		return rsEndpoint;
	}
	
	private final AtomicInteger counter = new AtomicInteger();
	/* (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#doRegisterServletContainer(org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider, java.lang.String, org.glassfish.jersey.server.ResourceConfig)
	 */
	private WhiteboardServletContainer registerContainer(String path, ResourceConfig config) {
		
		String applicationPath = config.getApplicationPath() == null ? "" : config.getApplicationPath();

		String contextPath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() -1) : basePath;
		contextPath += path.substring(0, path.length() - applicationPath.length());
		if(contextPath.endsWith("/")) {
			contextPath = contextPath.substring(0, contextPath.length() - 1);
		}
		if(!contextPath.startsWith("/")) {
			contextPath = "/" + contextPath;
		}

		String contextId;
		ServiceRegistration<ServletContextHelper> helper;
		if("/".equals(contextPath) && JAKARTA_RS_DEFAULT_APPLICATION.equals(config.getApplicationName())) {
			// The default application on the default path, just use the default context
			contextId = HTTP_WHITEBOARD_DEFAULT_CONTEXT_NAME;
			helper = null;
		} else {
			// This is a custom application so it gets a custom context
			contextId = String.format("ContextForRestWhiteboard.%s.instance.%s", httpId, counter.incrementAndGet());
			
			Dictionary<String, Object> contextHelperProps = new Hashtable<>();
			contextHelperProps.put(HTTP_WHITEBOARD_TARGET, httpWhiteboardTarget);
			contextHelperProps.put(HTTP_WHITEBOARD_CONTEXT_NAME, contextId);
			contextHelperProps.put(HTTP_WHITEBOARD_CONTEXT_PATH, contextPath);
			helper = context.registerService(ServletContextHelper.class, new ServletContextHelper() {}, contextHelperProps);
		}
		
		
		Dictionary<String, Object> servletProps = new Hashtable<>();
		
		String servletPath = applicationPath;
		if(!servletPath.startsWith("/")) {
			servletPath = "/" + servletPath;
		}
		if(servletPath.endsWith("/")) {
			servletPath += "*";
		} else {
			servletPath += "/*";
		}
		servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, servletPath);
		servletProps.put(HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED, Boolean.TRUE);
		servletProps.put(HTTP_WHITEBOARD_TARGET, httpWhiteboardTarget);
		servletProps.put(HTTP_WHITEBOARD_CONTEXT_SELECT, String.format("(osgi.http.whiteboard.context.name=%s)", contextId));
		
		WhiteboardServletContainer container = new WhiteboardServletContainer(config);
		
		RestContext rest = new RestContext(helper, context.registerService(Servlet.class, container, servletProps), container);
		
		pathsToServlets.put(path, rest);
		
		return container;
	}

	private void unregisterContainer(String path, WhiteboardServletContainer container) {
		RestContext rest = pathsToServlets.remove(path);
		if(rest != null) {
			rest.servletReg.unregister();
			if(rest.contextHelperReg != null)
				rest.contextHelperReg.unregister();
			rest.servlet.dispose();
		}
		container.dispose();
	}

	public void teardown(long i, TimeUnit seconds) {
		runtime.teardown(i, seconds);
	}

}
