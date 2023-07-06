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

import org.eclipse.osgitech.rest.annotations.RequireJerseyServlet;
import org.eclipse.osgitech.rest.binder.PromiseResponseHandlerBinder;
import org.eclipse.osgitech.rest.provider.jakartars.RuntimeDelegateService;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.glassfish.jersey.servlet.async.AsyncContextDelegateProviderImpl;
import org.glassfish.jersey.servlet.init.FilterUrlMappingsProviderImpl;

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

	/** SERVICE_LOCATOR_PREFIX */
	private static final String SERVICE_LOCATOR_PREFIX = "JakartaRESTJerseyWhiteboard-";

	/**
	 * 
	 */
	private static final long serialVersionUID = 6509888299005723799L;

	private ResourceConfig initialConfig = null;;

	private final AtomicBoolean initialized = new AtomicBoolean();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final ServiceLocator locator;

	public WhiteboardServletContainer(ResourceConfig config) {
		initialConfig = config;
		
		// Ensure that promise types can be returned by resource methods
		locator = ServiceLocatorFactory.getInstance()
			.create(SERVICE_LOCATOR_PREFIX + System.identityHashCode(this));
		ServiceLocatorUtilities.bind(locator, new PromiseResponseHandlerBinder());
		ServiceLocatorUtilities.addClasses(locator, AsyncContextDelegateProviderImpl.class, FilterUrlMappingsProviderImpl.class);
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
				
				getServletContext().setAttribute(ServletProperties.SERVICE_LOCATOR, locator);
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
				if (initialConfig != null) {
					this.reload(initialConfig);
					initialConfig = null;
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
			if (initialized.get()) {
				try {
					super.reload(configuration);
				} catch (IllegalStateException ise) {
					// TODO can we avoid this completely
					// Sometimes when reloading we find the application is in a bad state
					if(getApplicationHandler().getInjectionManager().isShutdown()) {
						try {
							this.initialConfig = configuration;
							init();
						} catch (ServletException e) {
							throw ise;
						}
					}
				}
			} else {
				initialConfig = configuration;
			}
		} finally {
			lock.writeLock().unlock();
		}
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
		locator.shutdown();
	}
}
