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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Application;

import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationContentProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider;
import org.eclipse.osgitech.rest.runtime.application.feature.WhiteboardFeature;
import org.osgi.framework.ServiceObjects;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * Special Jakartars application implementation that holds and updates all resource and extension given by the application provider
 * @author Mark Hoffmann
 * @since 15.07.2017
 */
public class JerseyApplication extends Application {

	private volatile Map<String, Class<?>> classes = new ConcurrentHashMap<>();
	private volatile Map<String, Object> singletons = new ConcurrentHashMap<>();
	private volatile Map<String, JakartarsExtensionProvider> extensions = new ConcurrentHashMap<>();
	private volatile Map<String, JakartarsApplicationContentProvider> contentProviders = new ConcurrentHashMap<>();
	private final String applicationName;
	private final Logger log = Logger.getLogger("jersey.application");
	private Map<String, Object> properties;
	private Application sourceApplication;
	private WhiteboardFeature whiteboardFeature;

	public JerseyApplication(String applicationName) {
		this.applicationName = applicationName;
	}

	public JerseyApplication(String applicationName, Application sourceApplication, Map<String, Object> additionalProperites) {
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
		if(!extensions.isEmpty()) {
			if(whiteboardFeature == null) {
				whiteboardFeature = new WhiteboardFeature(extensions);
			}
			resutlSingletons.add(whiteboardFeature);
		}
		return Collections.unmodifiableSet(resutlSingletons);
	}

	
	public void resetForReload() {
		if(whiteboardFeature != null) {
			whiteboardFeature.dispose();
			whiteboardFeature = null;
		} 
	}
	
	/**
	 * Returns the name of the whiteboard
	 * @return the name of the whiteboard
	 */
	public String getApplicationName() {
		return applicationName;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean addContent(JakartarsApplicationContentProvider contentProvider) {
		
		if (contentProvider == null) {
			if (log != null) {
				log.log(Level.WARNING, "A null service content provider was given to register as a Jakartars resource or extension");
			}
			return false;
		}
		String key = contentProvider.getId();
		contentProviders.put(key, contentProvider);
		if(contentProvider instanceof JakartarsExtensionProvider) {
			Class<?> extensionClass = contentProvider.getObjectClass();
			if (extensionClass == null) {
				contentProviders.remove(key);
				Object removed = extensions.remove(key);
				return removed != null;
			}
			JakartarsExtensionProvider result = extensions.put(key, (JakartarsExtensionProvider) contentProvider);
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
	 * Removes a content from the application
	 * @param contentProvider the provider of the contents to be removed
	 * @return Return <code>true</code>, if the content was removed
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean removeContent(JakartarsApplicationContentProvider contentProvider) {
		if (contentProvider == null) {
			if (log != null) {
				log.log(Level.WARNING, "A null resource provider was given to unregister as a Jakartars resource");
			}
			return false;
		}
		String key = contentProvider.getId();
		if(contentProvider instanceof JakartarsExtensionProvider) {
			synchronized (extensions) {
				JakartarsExtensionProvider ext = extensions.remove(key);
				if(ext != null) {
					if(whiteboardFeature != null) {
						whiteboardFeature.dispose(ext);
					}
				}
				// We can ignore singletons here, because we don't consider them here. 
			}
		} else if (contentProvider.isSingleton()) {
			synchronized (singletons) {
				Object obj = singletons.remove(key);
				if(obj != null) {
					log.fine("Unregistering service for resource " + contentProvider.getName() + " service " + obj);
					Object providerObj = contentProvider.getProviderObject();
					if(providerObj instanceof ServiceObjects) {
						ServiceObjects serviceObjs = (ServiceObjects) providerObj;
						try {
							serviceObjs.ungetService(obj);
						} catch(IllegalArgumentException e) {
							log.log(Level.SEVERE, "Cannot unget service for resource " + contentProvider.getName(), e);
						}
					}
				}				
			}
		} else {
			synchronized (classes) {
				classes.remove(key);
			}
		}
		synchronized (contentProviders) {
			return contentProviders.remove(key) != null;
		}
	}

	/**
	 * Returns all content providers
	 * @return a Collection of contentProviders
	 */
	public Collection<JakartarsApplicationContentProvider> getContentProviders(){
		return contentProviders.values();
	}

	/**
	 * @return the sourceApplication
	 */
	public Application getSourceApplication() {
		return sourceApplication;
	}
	
}
