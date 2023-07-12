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
package org.eclipse.osgitech.rest.factories;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.osgitech.rest.binder.PrototypeServiceBinder;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationContentProvider;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.osgi.framework.ServiceObjects;

/**
 * HK2 creation factory for Jakartars resource instance. These factory instances will be bound using the {@link PrototypeServiceBinder}.
 * The factory is responsible to create or releasing a certain Jakartars resource instances, at request time.
 * @param <T> the type of the resource, which is the class type
 * @author Mark Hoffmann
 * @since 12.07.2017
 */
public class JerseyResourceInstanceFactory<T> implements InjectableFactory<T> {

	private volatile Set<Object> instanceCache = new HashSet<>();
	private JerseyApplicationContentProvider provider;
	private ServiceObjects<Object> serviceObjects;
	private InjectionManager injectionManager;
	
	/**
	 * Creates a new instance. A service reference will be cached lazily, on the first request
	 * @param clazz the resource class
	 */
	public JerseyResourceInstanceFactory(JerseyApplicationContentProvider provider) {
		this.provider = provider;
		serviceObjects = provider.getProviderObject();
	}

	/* (non-Javadoc)
	 * @see org.glassfish.hk2.api.Factory#dispose(java.lang.Object)
	 */
	@Override
	public void dispose(T object) {
		disposeInstance(object);
	}

	/* (non-Javadoc)
	 * @see org.glassfish.hk2.api.Factory#provide()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T provide() {
		try {
			// If the service objects is null, the service is obviously gone and we return null to avoid exception in jersey
			if (serviceObjects == null) {
				return null;
			}
			Object instance = serviceObjects.getService();
			if(instance == null) {
				return null;
			}
			if(injectionManager != null) {
				injectionManager.inject(instance);
			}
			synchronized (instanceCache) {
				instanceCache.add(instance);
			}
			return (T)instance;
		} catch (Exception e) {
			if (e instanceof IllegalStateException) {
				throw e;
			}
			throw new IllegalStateException("Cannot create prototype instance for class " + provider.getId(), e);
		}
	}

	/**
	 * Cleans up all resources. If a service reference was given via constructor, the reference is now <code>null</code>
	 * After calling dispose, a new instance has to be created
	 */
	public void dispose() {
		// release all cached service instances
		if (serviceObjects != null) {
			instanceCache.forEach((i) -> serviceObjects.ungetService(i));
		}
		instanceCache.clear();
	}
	
	/**
	 * Return the size of the cached instances
	 * @return the size of the cached instances
	 */
	public int getCacheInstanceCount() {
		return instanceCache.size();
	}

	/**
	 * Disposes a service instance. If it is a prototype instance, it will be removed from the cache.
	 * @param instance the instance to be released
	 */
	private void disposeInstance(T instance) {
		if (instance == null) {
			return;
		}
		if (instanceCache.remove(instance)) {
			try {
				serviceObjects.ungetService(instance);
			} catch (Exception e) {
				if (e instanceof IllegalStateException) {
					throw e;
				}
				throw new IllegalStateException("Error disposing instance " + instance, e);
			}
		}
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.factories.InjectableFactory#setInjectionManager(org.glassfish.jersey.internal.inject.InjectionManager)
	 */
	@Override
	public void setInjectionManager(InjectionManager injectionManager) {
		this.injectionManager = injectionManager;
	}

}
