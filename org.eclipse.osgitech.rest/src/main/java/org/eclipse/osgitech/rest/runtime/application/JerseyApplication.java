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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.runtime.application.feature.WhiteboardFeature;
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
	private final Map<String, JerseyApplicationContentProvider> contentProviders = new ConcurrentHashMap<>();
	private final String applicationName;
	private final Logger log = Logger.getLogger("jersey.application");
	private final Map<String, Object> properties;
	private final Application sourceApplication;
	private final WhiteboardFeature whiteboardFeature;

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
		Set<Class<?>> resutlClasses = new HashSet<>();
		resutlClasses.addAll(classes.values());
		resutlClasses.addAll(sourceApplication.getClasses());
		return Collections.unmodifiableSet(resutlClasses);
	}

	/* 
	 * (non-Javadoc)
	 * @see jakarta.ws.rs.core.Application#getSingletons()
	 */
	@SuppressWarnings("deprecation")
	@Override
	public Set<Object> getSingletons() {
		Set<Object> resutlSingletons = new HashSet<>();
		resutlSingletons.addAll(singletons.values());
		resutlSingletons.addAll(sourceApplication.getSingletons());
		resutlSingletons.add(whiteboardFeature);
		return Collections.unmodifiableSet(resutlSingletons);
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
		} else if (contentProvider.isSingleton()) {
			Class<?> resourceClass = contentProvider.getObjectClass();
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
			Class<?> resourceClass = contentProvider.getObjectClass();
			if (resourceClass == null) {
				contentProviders.remove(key);
				Object removed = classes.remove(key);
				return removed != null;
			}
			Object result = classes.put(key, resourceClass);
			return !resourceClass.equals(result) || result == null;
		}
		
	}

	/**
	 * @return the sourceApplication
	 */
	public Application getSourceApplication() {
		return sourceApplication;
	}
	
}
