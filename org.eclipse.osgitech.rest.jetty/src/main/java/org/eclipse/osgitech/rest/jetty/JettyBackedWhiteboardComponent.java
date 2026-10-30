/**
 * Copyright (c) 2012 - 2024 Data In Motion and others.
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
 *     Dirk Fauth - add CRaC support
 */
package org.eclipse.osgitech.rest.jetty;

import static org.eclipse.osgitech.rest.provider.JerseyConstants.JERSEY_DISABLE_SESSION;
import static org.eclipse.osgitech.rest.provider.JerseyConstants.WHITEBOARD_DEFAULT_CONTEXT_PATH;
import static org.eclipse.osgitech.rest.provider.JerseyConstants.WHITEBOARD_DEFAULT_HOST;
import static org.eclipse.osgitech.rest.provider.JerseyConstants.WHITEBOARD_DEFAULT_PORT;
import static org.eclipse.osgitech.rest.provider.JerseyConstants.WHITEBOARD_DEFAULT_SCHEMA;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;

import java.net.URI;
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
import org.eclipse.osgitech.rest.runtime.JerseyServiceRuntime;
import org.eclipse.osgitech.rest.runtime.WhiteboardServletContainer;
import org.glassfish.jersey.server.ResourceConfig;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;

/**
 * A configurable component, that establishes a whiteboard
 * @author Mark Hoffmann
 * @since 11.10.2017
 */

@ProvideRuntimeAdapter("jetty")
@Component(name = "JakartarsWhiteboardComponent", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class JettyBackedWhiteboardComponent {

	Logger logger = Logger.getLogger(JettyBackedWhiteboardComponent.class.getName());
	
	private JerseyServiceRuntime<WhiteboardServletContainer> serviceRuntime;
	
	// need to keep a strong reference to avoid that the resource gets garbage collected
	@SuppressWarnings("unused")
	private JettyCracResource cracHandler;

	public enum State {
		INIT, STARTED, STOPPED, EXCEPTION
	}
	private volatile Server jettyServer;
	private Integer port = JerseyConstants.WHITEBOARD_DEFAULT_PORT;
	private String contextPath = JerseyConstants.WHITEBOARD_DEFAULT_CONTEXT_PATH;
	private String[] uris = {WHITEBOARD_DEFAULT_SCHEMA + "://" + WHITEBOARD_DEFAULT_HOST 
			+ ":" + WHITEBOARD_DEFAULT_PORT + WHITEBOARD_DEFAULT_CONTEXT_PATH};
	private boolean disableSession;
	private final Map<String, ServletContextHandler> handlerMap = new HashMap<>();
	private final HandlerList handlers = new HandlerList();

	/**
	 * Called on component activation
	 * @param componentContext the component context
	 * @throws ConfigurationException 
	 */
	@Activate
	public void activate(BundleContext context, Map<String, Object> properties) throws ConfigurationException {
		
		serviceRuntime = new JerseyServiceRuntime<>(context, this::createContainerForPath, 
				this::destroyContainer);
		
		doUpdateProperties(properties);
		
		createServerAndContext();
		startServer();
		
		
		serviceRuntime.start(getServiceRuntimeProperties(properties));

		try {
			Class.forName("org.crac.Resource");
			
			// org.crac.Resource was found, so we create an instance of the JettyCracResource
			cracHandler = new JettyCracResource(this);
		} catch (ClassNotFoundException e) {
			// org.crac.Resource could not be found
			// we simply do nothing, as CRaC support is not available
		}
		
	}

	private Map<String, Object> getServiceRuntimeProperties(Map<String, Object> properties) {
		Map<String, Object> runtimeProperties = new HashMap<>(properties);
		runtimeProperties.put(JAKARTA_RS_SERVICE_ENDPOINT, uris);
		return runtimeProperties;
	}

	/**
	 * Called on component modification
	 * @param context the component context
	 * @throws ConfigurationException 
	 */
	@Modified
	public void modified(Map<String, Object> props) throws ConfigurationException {
		
		
		Integer oldPort = port;
		String oldContextPath = contextPath;
		doUpdateProperties(props);
		boolean portChanged = !this.port.equals(oldPort);
		boolean pathChanged = !this.contextPath.equals(oldContextPath);
		
		if (pathChanged || portChanged) {
			stopContextHandlers();
			stopServer();
			createServerAndContext();
			startServer();
		}
		serviceRuntime.update(getServiceRuntimeProperties(props));
	}



	/**
	 * Called on component de-activation
	 * @param context the component context
	 */
	@Deactivate
	public void deactivate(ComponentContext context) {
		serviceRuntime.teardown(5, TimeUnit.SECONDS);
		stopContextHandlers();
		stopServer();
	}

	private String[] getURLs(Map<String, Object> props) {
		StringBuilder sb = new StringBuilder();
		String schema = JerseyHelper.getPropertyWithDefault(props, JerseyConstants.JERSEY_SCHEMA,
				JerseyConstants.WHITEBOARD_DEFAULT_SCHEMA);
		sb.append(schema);
		sb.append("://");
		String host = JerseyHelper.getPropertyWithDefault(props, JerseyConstants.JERSEY_HOST,
				JerseyConstants.WHITEBOARD_DEFAULT_HOST);
		sb.append(host);
		Object port = JerseyHelper.getPropertyWithDefault(props, JerseyConstants.JERSEY_PORT, null);
		if (port != null) {
			sb.append(":");
			sb.append(port.toString());
		}
		String path = JerseyHelper.getPropertyWithDefault(props, JerseyConstants.JERSEY_CONTEXT_PATH,
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
	private WhiteboardServletContainer createContainerForPath(String path, ResourceConfig config) {
		WhiteboardServletContainer container = new WhiteboardServletContainer(config);
		ServletHolder servlet = new ServletHolder(container);
		servlet.setAsyncSupported(true);
		ServletContextHandler handler = createContext(path);
		handler.addServlet(servlet, "/*");
		if ("/".equals(path)) {
			handlers.addHandler(handler);
		} else {
			handlers.prependHandler(handler);
		}
		try {
			handler.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Cannot start server context handler for context: " + path, e);
		}
		return container;
	}

	private void destroyContainer(String path, WhiteboardServletContainer container) {
		removeContextHandler(path);
		container.dispose();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.osgitech.rest.runtime.common.AbstractJerseyServiceRuntime#
	 * doUpdateProperties(org.osgi.service.component.ComponentContext)
	 */
	private void doUpdateProperties(Map<String, Object> props) {
		this.disableSession = JerseyHelper.getPropertyWithDefault(props, JERSEY_DISABLE_SESSION, true);
		this.uris = getURLs(props);
		// This validates all of the supplied uris
		URI[] uris = new URI[this.uris.length];
		for (int i = 0; i < uris.length; i++) {
			uris[i] = URI.create(this.uris[i]);
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
		if(disableSession == false) {
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
	
	/**
	 * 
	 * @return the Jetty server managed by this class. Can be <code>null</code>.
	 */
	Server getJettyServer() {
		return jettyServer;
	}
}
