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
package org.eclipse.osgitech.rest.runtime.application;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.osgitech.rest.binder.PromiseResponseHandlerBinder;
import org.eclipse.osgitech.rest.binder.PrototypeServiceBinder;
import org.eclipse.osgitech.rest.factories.InjectableFactory;
import org.eclipse.osgitech.rest.factories.JerseyResourceInstanceFactory;
import org.eclipse.osgitech.rest.runtime.application.feature.WhiteboardFeature;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.server.spi.AbstractContainerLifecycleListener;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.servlet.async.AsyncContextDelegateProviderImpl;
import org.glassfish.jersey.servlet.init.FilterUrlMappingsProviderImpl;
import org.osgi.framework.ServiceObjects;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.core.Application;

/**
 * Special Jakartars application implementation that holds and updates all resource and extension given by the application provider
 * @author Mark Hoffmann
 * @since 15.07.2017
 */
public class JerseyApplication extends Application {

	private final Map<String, Class<?>> classes = new HashMap<>();
	private final Map<String, Object> singletons = new HashMap<>();
	private final Map<String, JerseyExtensionProvider> extensions = new HashMap<>();
	private final Map<String, JerseyApplicationContentProvider> contentProviders = new HashMap<>();
	private final String applicationName;
	private final Logger log = Logger.getLogger("jersey.application");
	private final Map<String, Object> properties;
	private final Application sourceApplication;
	private final Map<Class<?>, InjectableFactory<?>> factories = new HashMap<>();
	private final WhiteboardFeature whiteboardFeature;
	private final Set<Object> appSingletons;

	@SuppressWarnings("deprecation")
	public JerseyApplication(String applicationName, Application sourceApplication, Map<String, Object> additionalProperites,
			List<JerseyApplicationContentProvider> providers) {
		this.applicationName = applicationName;
		this.sourceApplication = sourceApplication;
		Map<String, Object> props = new HashMap<String, Object>();
		if(additionalProperites != null) {
			props.put(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_SERVICE_PROPERTIES, additionalProperites);
		}
		if(sourceApplication.getProperties() != null) {
			props.putAll(sourceApplication.getProperties());
		}
		properties = Collections.unmodifiableMap(props);
		
		providers.forEach(this::addContent);
		
		whiteboardFeature = new WhiteboardFeature(extensions);

		PrototypeServiceBinder resourceFactory  = new PrototypeServiceBinder();
		factories.forEach(resourceFactory::register);
		
		appSingletons = new HashSet<>();
		appSingletons.addAll(this.singletons.values());
		appSingletons.addAll(sourceApplication.getSingletons());
		appSingletons.add(whiteboardFeature);
		appSingletons.add(resourceFactory);
		appSingletons.add(new ContainerLifecycleTracker());
		
	}

	@Override
	public Map<String, Object> getProperties() {
		return properties;
	}
	
	/* 
	 * (non-Javadoc)
	 * @see jakarta.ws.rs.core.Application#getClasses()
	 */
	@Override
	public Set<Class<?>> getClasses() {
		return Stream.of(classes.values().stream(), sourceApplication.getClasses().stream(),
				Stream.of(PromiseResponseHandlerBinder.class, AsyncContextDelegateProviderImpl.class, FilterUrlMappingsProviderImpl.class))
				.flatMap(Function.identity())
				.collect(toUnmodifiableSet());
	}

	/* 
	 * (non-Javadoc)
	 * @see jakarta.ws.rs.core.Application#getSingletons()
	 */
	@Override
	public Set<Object> getSingletons() {
		return Collections.unmodifiableSet(appSingletons);
	}

	
	public void dispose() {
		whiteboardFeature.dispose();
		singletons.forEach((k,v) -> {
			JerseyApplicationContentProvider provider = contentProviders.get(k);
			Object providerObj = provider.getProviderObject();
			if(providerObj instanceof ServiceObjects) {
				@SuppressWarnings("unchecked")
				ServiceObjects<Object> serviceObjs = (ServiceObjects<Object>) providerObj;
				try {
					serviceObjs.ungetService(v);
				} catch(IllegalArgumentException e) {
					log.log(Level.SEVERE, "Cannot unget service for resource " + provider.getName(), e);
				}
			}
		});
		singletons.clear();
	}
	
	/**
	 * Returns the name of the whiteboard
	 * @return the name of the whiteboard
	 */
	public String getApplicationName() {
		return applicationName;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private boolean addContent(JerseyApplicationContentProvider contentProvider) {
		
		if (contentProvider == null) {
			if (log != null) {
				log.log(Level.WARNING, "A null service content provider was given to register as a Jakartars resource or extension");
			}
			return false;
		}
		String key = contentProvider.getId();
		contentProviders.put(key, contentProvider);
		if(contentProvider instanceof JerseyExtensionProvider) {
			Class<?> extensionClass = contentProvider.getObjectClass();
			if (extensionClass == null) {
				contentProviders.remove(key);
				Object removed = extensions.remove(key);
				return removed != null;
			}
			JerseyExtensionProvider result = extensions.put(key, (JerseyExtensionProvider) contentProvider);
			return  result == null || !extensionClass.equals(result.getObjectClass());
		} else { 
			Class<?> resourceClass = contentProvider.getObjectClass();
			factories.put(resourceClass, new JerseyResourceInstanceFactory<>(contentProvider));
			if (contentProvider.isSingleton()) {
				Object result = singletons.get(key);
				if(result == null || !result.getClass().equals(resourceClass)){
					Object providerObject = contentProvider.getProviderObject();
					/*
					 * Maybe we are in shutdown mode
					 */
					if (providerObject == null) {
						contentProviders.remove(key);
						Object removed = singletons.remove(key);
						return removed != null;
					}
					Object service = ((ServiceObjects<?>) providerObject).getService();
					if (service == null) {
						contentProviders.remove(key);
						Object removed = singletons.remove(key);
						return removed != null;
					}
					result = singletons.put(key, service);
					if(result != null) {
						((ServiceObjects) contentProvider.getProviderObject()).ungetService(result);
					}
					return true;
				}
				return false;
			} else {
				if (resourceClass == null) {
					contentProviders.remove(key);
					Object removed = classes.remove(key);
					return removed != null;
				}
				Object result = classes.put(key, resourceClass);
				return !resourceClass.equals(result) || result == null;
			}
		}
		
	}

	/**
	 * @return the sourceApplication
	 */
	public Application getSourceApplication() {
		return sourceApplication;
	}
	
	/**
	 * Used to set the injection manager in the factories, and to teardown the instances
	 * afterwards
	 */
	private class ContainerLifecycleTracker extends AbstractContainerLifecycleListener {

		@Override
		public void onStartup(Container container) {
			InjectionManager im = container.getApplicationHandler().getInjectionManager();
			factories.values().forEach(f -> f.setInjectionManager(im));
		}

		@Override
		public void onShutdown(Container container) {
			dispose();
		}
	}
}
