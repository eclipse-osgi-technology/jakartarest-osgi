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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.osgitech.rest.annotations.ProvideRuntimeAdapter;
import org.eclipse.osgitech.rest.helper.JakartarsHelper;
import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.eclipse.osgitech.rest.provider.JerseyConstants;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.runtime.AbstractJerseyServiceRuntime;
import org.eclipse.osgitech.rest.runtime.ResourceConfigWrapper;
import org.eclipse.osgitech.rest.runtime.WhiteboardServletContainer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;

/**
 * Implementation of the {@link JakartarsServiceRuntime} for a Jetty backed implementation
 * 
 * @author Mark Hoffmann
 * @since 12.07.2017
 */
@ProvideRuntimeAdapter("jetty")
public class JerseyServiceRuntime extends AbstractJerseyServiceRuntime {

	public enum State {
		INIT, STARTED, STOPPED, EXCEPTION
	}
	private volatile Server jettyServer;
	private Integer port = JerseyConstants.WHITEBOARD_DEFAULT_PORT;
	private String contextPath = JerseyConstants.WHITEBOARD_DEFAULT_CONTEXT_PATH;
	private Logger logger = Logger.getLogger("Jakartars.serviceRuntime");
	private final Map<String, ServletContextHandler> handlerMap = new HashMap<>();
	private final HandlerList handlers = new HandlerList();

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#
	 * doInitialize(org.osgi.service.component.ComponentContext)
	 */
	@Override
	protected void doInitialize(ComponentContext context) {
		createServerAndContext();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#modified(
	 * org.osgi.service.component.ComponentContext)
	 */
	public void doModified(ComponentContext ctx) throws ConfigurationException {
		Integer oldPort = port;
		String oldContextPath = contextPath;
		updateProperties(ctx);
		boolean portChanged = !this.port.equals(oldPort);
		boolean pathChanged = !this.contextPath.equals(oldContextPath);

		if (!pathChanged && !portChanged) {
			return;
		}
		// if port changed, both parts need to be restarted, no matter, if the context
		// path has changed
		if (portChanged || pathChanged) {
			
			applicationContainerMap.values().forEach(ap -> new ArrayList<ServletContainer>(ap.getServletContainers()).forEach(ap::removeServletContainer));
			
			stopContextHandlers();
			stopServer();
			createServerAndContext();
			startServer();
			
			applicationContainerMap.values().forEach(ap -> {
				doRegisterServletContext(ap, ap.getPath());
			});
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#doStartup()
	 */
	@Override
	public void doStartup() {
		startServer();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#doTeardown(
	 * )
	 */
	@Override
	protected void doTeardown() {
		stopContextHandlers();
		stopServer();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#getURLs(org
	 * .osgi.service.component.ComponentContext)
	 */
	public String[] getURLs(ComponentContext context) {
		StringBuilder sb = new StringBuilder();
		String schema = JerseyHelper.getPropertyWithDefault(context, JerseyConstants.JERSEY_SCHEMA,
				JerseyConstants.WHITEBOARD_DEFAULT_SCHEMA);
		sb.append(schema);
		sb.append("://");
		String host = JerseyHelper.getPropertyWithDefault(context, JerseyConstants.JERSEY_HOST,
				JerseyConstants.WHITEBOARD_DEFAULT_HOST);
		sb.append(host);
		Object port = JerseyHelper.getPropertyWithDefault(context, JerseyConstants.JERSEY_PORT, null);
		if (port != null) {
			sb.append(":");
			sb.append(port.toString());
		}
		String path = JerseyHelper.getPropertyWithDefault(context, JerseyConstants.JERSEY_CONTEXT_PATH,
				JerseyConstants.WHITEBOARD_DEFAULT_CONTEXT_PATH);
		path = JakartarsHelper.toServletPath(path);
		sb.append(path);
		return new String[] { sb.substring(0, sb.length() - 1) };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#
	 * doRegisterServletContainer(org.glassfish.jersey.servlet.ServletContainer,
	 * org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider)
	 */
	@Override
	protected void doRegisterServletContext(JakartarsApplicationProvider applicationProvider, String path,
			ResourceConfig config) {
		WhiteboardServletContainer container = new WhiteboardServletContainer(config, applicationProvider);
		if (!applicationProvider.getServletContainers().isEmpty()) {
			throw new IllegalStateException("There is alread a ServletContainer registered for this application "
					+ applicationProvider.getId());
		}
		applicationProvider.getServletContainers().add(container);
		ServletHolder servlet = new ServletHolder(container);
		servlet.setAsyncSupported(true);
		ServletContextHandler handler = createContext(path);
		handler.addServlet(servlet, "/");
		if (applicationProvider.isDefault()) {
			handlers.addHandler(handler);
		} else {
			handlers.prependHandler(handler);
		}
		try {
			handler.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Cannot start server context handler for context: " + path, e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#
	 * doRegisterServletContainer(org.glassfish.jersey.servlet.ServletContainer,
	 * org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider)
	 */
	@Override
	protected void doRegisterServletContext(JakartarsApplicationProvider applicationProvider, String path) {
		ResourceConfigWrapper config = createResourceConfig(applicationProvider);
		WhiteboardServletContainer container = new WhiteboardServletContainer(config, applicationProvider);
		if (!applicationProvider.getServletContainers().isEmpty()) {
			throw new IllegalStateException("There is already a ServletContainer registered for this application "
					+ applicationProvider.getId());
		}
		applicationProvider.getServletContainers().add(container);
		ServletHolder servlet = new ServletHolder(container);
		servlet.setAsyncSupported(true);
		ServletContextHandler handler = createContext(path);
//		HelloWorld s = new HelloWorld(path);
//		ServletHolder sh = new ServletHolder(s);
//		handler.addServlet(sh, "/bla");
		handler.addServlet(servlet, "/*");
		if (applicationProvider.isDefault()) {
			handlers.addHandler(handler);
		} else {
			handlers.prependHandler(handler);
		}
		try {
			handler.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Cannot start server context handler for context: " + path, e);
		}
	}

	@Override
	protected void doUnregisterApplication(JakartarsApplicationProvider applicationProvider) {
		removeContextHandler(applicationProvider.getPath());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#
	 * doUpdateProperties(org.osgi.service.component.ComponentContext)
	 */
	protected void doUpdateProperties(ComponentContext ctx) {
		String[] urls = getURLs(ctx);
		URI[] uris = new URI[urls.length];
		for (int i = 0; i < urls.length; i++) {
			uris[i] = URI.create(urls[i]);
		}
		URI uri = uris[0];
		if (uri.getPort() > 0) {
			port = uri.getPort();
		}
		if (uri.getPath() != null) {
			contextPath = uri.getPath();
		}
	}
	
	private String getContextPath(String path) {
		String thisPath = path == null ? "" : path;
		thisPath = thisPath.replace("/*", "");
		thisPath = thisPath.endsWith("/") ? thisPath.substring(0, thisPath.length() -1) : thisPath;
		thisPath = thisPath.startsWith("/") ? thisPath.substring(1) : thisPath;
		String ctx = contextPath;
		ctx = ctx.endsWith("/") ? ctx.substring(0, ctx.length() -1) : ctx;
		if (!thisPath.isEmpty()) {
			ctx = ctx + "/" + thisPath;
		}
		return ctx;
	}
	
	private ServletContextHandler createContext(String path) {
		ServletContextHandler contextHandler = new ServletContextHandler();
		String ctxPath = getContextPath(path);
		Object disableSessions = context.getProperties().get(JerseyConstants.JERSEY_DISABLE_SESSION);
		if(disableSessions == null || !Boolean.valueOf(disableSessions.toString())) {
			contextHandler.setSessionHandler(new SessionHandler());
		}
		contextHandler.setServer(jettyServer);
		contextHandler.setContextPath(ctxPath);
		if (!handlerMap.containsKey(path)) {
			handlerMap.put(path, contextHandler);
		}
		logger.fine("Created white-board server context handler for context: " + path);
		return contextHandler;
	}

	/**
	 * Stopps the Jetty context handler for the given context path;
	 */
	private void removeContextHandler(String path) {
		ServletContextHandler handler = handlerMap.remove(path);
		if (handler == null) {
			logger.log(Level.WARNING, "Try to stop Jetty context handler for path " + path + ", but there is none");
			return;
		}
		if (handler.isStopped()) {
			logger.log(Level.WARNING, "Try to stop Jetty context handler for path " + path + ", but it was already stopped");
			return;
		}
		try {
			handlers.removeHandler(handler);
			handler.stop();
			handler.destroy();
			handler = null;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error stopping Jetty context handler for path " + path, e);
		}
	}
	
	private void stopContextHandlers() {
		handlerMap.keySet().forEach(this::removeContextHandler);
	}

	/**
	 * Creates the Jetty server and initializes the current context handler
	 */
	private void createServerAndContext() {
		try {
			if (jettyServer != null && !jettyServer.isStopped()) {
				logger.log(Level.WARNING,
						"Stopping Jakartars whiteboard server on startup, but it wasn't exepected to run");
				stopContextHandlers();
				stopServer();
			}
			jettyServer = new Server(port);
			jettyServer.setHandler(handlers);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error starting Jakartars whiteboard because of an exception", e);
		}
	}

	/**
	 * Starts the Jetty server
	 */
	private void startServer() {
		if (jettyServer != null && !jettyServer.isRunning()) {

			JettyServerRunnable jettyServerRunnable = new JettyServerRunnable(jettyServer, port);

			Executors.newSingleThreadExecutor().submit(jettyServerRunnable);
			if (jettyServerRunnable.isStarted(5, TimeUnit.SECONDS)) {

				logger.info("Started Jakartars whiteboard server for port: " + port + " and context: " + contextPath);

			} else {
				switch (jettyServerRunnable.getState()) {
				case INIT:

					logger.severe("Started Jakartars whiteboard server for port: " + port + " and context: " + contextPath
							+ " took to long");
					throw new IllegalStateException("Server Startup took too long");
				case STARTED:
					// finished in last second
					break;
				case STOPPED:

					logger.info("Started Jakartars whiteboard server for port: " + port + " and context: " + contextPath
							+ " was stopped with unknown reasons");
					throw new IllegalStateException("Server Startup was stopped with unknown reasons");

				case EXCEPTION:
					logger.severe("Started Jakartars whiteboard server for port: " + port + " and context: " + contextPath
							+ " throws exception");
					throw new IllegalStateException("Server Startup was stopped with exception",
							jettyServerRunnable.getThrowable());

				default:
					throw new IllegalStateException("Server Startup - has unknown state ");
				}
			}
		}
	}

	/**
	 * Stopps the Jetty server;
	 */
	private void stopServer() {
		if (jettyServer == null) {
			logger.log(Level.WARNING, "Try to stop Jakartars whiteboard server, but there is none");
			return;
		}
		if (jettyServer.isStopped()) {
			logger.log(Level.WARNING, "Try to stop Jakartars whiteboard server, but it was already stopped");
			return;
		}
		try {
			jettyServer.stop();
			jettyServer.destroy();
			jettyServer = null;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error stopping Jetty server", e);
		}
	}

}
