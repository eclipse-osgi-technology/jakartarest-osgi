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
package org.eclipse.osgitech.rest.binder;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.osgitech.rest.factories.JerseyResourceInstanceFactory;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;

/**
 * OSGi injection binder for HK2, that is used in Jersey. This binder is responsible for
 * the creation of JakartaRs resource instance of service references of type PROTOTYPE
 * @author Mark Hoffmann
 * @since 12.07.2017
 */
public class PrototypeServiceBinder extends AbstractBinder {

	private final Map<Class<?>, Factory<?>> factoryMap = new HashMap<>();

	/* (non-Javadoc)
	 * @see org.glassfish.hk2.utilities.binding.AbstractBinder#configure()
	 */
	@Override
	protected void configure() {
		// bind factories in two different scopes
		factoryMap.forEach((K,V)->{
			bindFactory(V).to(K).in(RequestScoped.class);
			bindFactory(V).to(K).in(PerLookup.class);
		});
	}

	/**
	 * Registers the given class with the HK2 creation factory
	 * @param clazz the class type the factory is for
	 * @param factory the HK2 factory to create and dispose objects
	 */
	public void register(Class<?> clazz, Factory<?> factory) {
		if (clazz == null || factory == null) {
			return;
		}
		factoryMap.put(clazz, factory);
	}

	/**
	 * Unregisters the factory for the given class
	 * @param clazz the class to remove the factory
	 */
	public void unregister(Class<?> clazz) {
		if (clazz == null) {
			return;
		}
		factoryMap.remove(clazz);
	}

	/**
	 * Releases all resources
	 */
	public void dispose() {
		factoryMap.forEach((k,v)->{
			if (v instanceof JerseyResourceInstanceFactory<?>) {
				((JerseyResourceInstanceFactory<?>)v).dispose();
			}
		});
		factoryMap.clear();
	}

}
