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

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.annotations.RequireJerseyServlet;
import org.eclipse.osgitech.rest.provider.jakartars.RuntimeDelegateService;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * As Wrapper for the {@link ServletContainer} that locks the Servlet while its configuration is reloaded.
 * Furthermore it takes care that a reload is done, if a new configuration comes available while it is initialized
 * @author Juergen Albert
 * @since 1.0
 */
@RequireJerseyServlet
public class WhiteboardServletContainer extends ServletContainer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6509888299005723799L;

	private static final Logger logger = Logger.getLogger("jersey.servletContainer");

	private ResourceConfig initialConfig = null;;

	private final AtomicBoolean initialized = new AtomicBoolean();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public WhiteboardServletContainer(ResourceConfig config) {
		initialConfig = config;
	}

	/* (non-Javadoc)
	 * @see org.glassfish.jersey.servlet.ServletContainer#init()
	 */
	@Override
	public void init() throws ServletException {
		
		lock.writeLock().lock();
		
		try {
			ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(RuntimeDelegateService.class.getClassLoader());
				
				super.init();
				// we have to wait until the injection manager is available on first start
				Future<?> future = Executors.newSingleThreadExecutor().submit(()->{
					ApplicationHandler handler = getApplicationHandler();
					while(handler.getInjectionManager() == null) {
						try {
							Thread.sleep(10l);
						} catch (InterruptedException e) {
						}
					}
				});
				future.get();
				initialized.set(true);
				ResourceConfig config = initialConfig;
				initialConfig = null;
				if (config != null && !doReload(config)) {
					throw new ServletException("The Jakarta REST application could not be loaded, see the log for the cause");
				}
			} catch (Exception e) {
				if (e instanceof ServletException) {
					throw (ServletException)e;
				} else {
					throw new ServletException(e);
				}
			} finally {
				Thread.currentThread().setContextClassLoader(oldTccl);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.glassfish.jersey.servlet.ServletContainer#reload(org.glassfish.jersey.server.ResourceConfig)
	 */
	@Override
	public void reload(ResourceConfig configuration) {
		lock.writeLock().lock();
		try {
			if (!initialized.get()) {
				initialConfig = configuration;
				return;
			}
			if (isApplicationShutDown()) {
				// A previous reload failed, Jersey would only fail again on shutting down the dead application
				logger.log(Level.INFO, "The Jakarta REST application is shut down from a previous failure, re-initialising the servlet container");
			} else if (doReload(configuration)) {
				return;
			} else {
				/*
				 * Jersey shuts the current application down before it builds the new one. When building the
				 * new one fails, the servlet keeps pointing to the shut down application and every request
				 * fails (#60, #70). Re-initialise the whole servlet container with the new configuration, so
				 * that the application is served again, or the failure is at least reported to the caller.
				 */
				logger.log(Level.INFO, "Re-initialising the servlet container after the failed reload of the Jakarta REST application");
			}
			try {
				this.initialConfig = configuration;
				init();
			} catch (ServletException e) {
				throw new IllegalStateException("Re-initialising the servlet container after a failed reload of the "
						+ "Jakarta REST application failed as well", e);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * Reloads the application with the given configuration and reports whether the application is
	 * served afterwards. Jersey only catches a {@link ServletException} while reloading, any other
	 * exception is caught here, and both leave the servlet with the shut down application handler.
	 * The outcome is therefore checked on the handler instead of relying on the exception type.
	 * @param configuration the new configuration
	 * @return <code>true</code> if the application is served afterwards, <code>false</code> if the
	 * reload failed and the application handler is shut down
	 */
	private boolean doReload(ResourceConfig configuration) {
		RuntimeException failure = null;
		try {
			super.reload(configuration);
		} catch (RuntimeException e) {
			failure = e;
		}
		boolean shutDown = isApplicationShutDown();
		if (failure == null && !shutDown) {
			return true;
		}
		if (failure != null) {
			logger.log(Level.SEVERE, "Reloading the Jakarta REST application failed", failure);
		} else {
			logger.log(Level.SEVERE, "Reloading the Jakarta REST application failed, the cause has been logged by Jersey");
		}
		return !shutDown;
	}
	
	/**
	 * @return <code>true</code> if the application handler the servlet currently delegates to is shut down
	 */
	private boolean isApplicationShutDown() {
		ApplicationHandler handler = getApplicationHandler();
		InjectionManager injectionManager = handler == null ? null : handler.getInjectionManager();
		return injectionManager == null || injectionManager.isShutdown();
	}
	
	/* (non-Javadoc)
	 * @see org.glassfish.jersey.servlet.ServletContainer#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		lock.readLock().lock();
		try {
			super.service(request, response);
		} finally {
			lock.readLock().unlock();
		}
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.glassfish.jersey.servlet.ServletContainer#destroy()
	 */
	@Override
	public void destroy() {
		lock.writeLock().lock();
		try {
			initialized.set(false);
			super.destroy();
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public ResourceConfig getConfiguration() {
		lock.readLock().lock();
		try {
			if(initialConfig != null) {
				return initialConfig;
			} else {
				return super.getConfiguration();
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public void dispose() {
	}
}
