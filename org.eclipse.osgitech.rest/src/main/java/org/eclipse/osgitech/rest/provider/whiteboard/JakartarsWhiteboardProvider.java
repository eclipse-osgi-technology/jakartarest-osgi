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
package org.eclipse.osgitech.rest.provider.whiteboard;

import java.util.Map;

import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;

/**
 * Provider for a whiteboard component
 * @author Mark Hoffmann
 * @since 11.10.2017
 */
public interface JakartarsWhiteboardProvider {
	
	/**
	 * Initializes the whiteboard
	 * @param context the component context
	 * @throws ConfigurationException thrown on mis-configuration
	 */
	public void initialize(ComponentContext context) throws ConfigurationException;
	
	/**
	 * Called on whiteboard modification
	 * @param context the component context
	 * @throws ConfigurationException thrown on mis-configuration
	 */
	public void modified(ComponentContext context) throws ConfigurationException;
	
	/**
	 * Starts the whiteboard
	 */
	public void startup();
	
	/**
	 * Tears the whiteboard down
	 */
	public void teardown();
	
	/**
	 * Returns all urls that belong to the handler
	 * @param context the component context
	 * @return an array of URLs
	 */
	public String[] getURLs(ComponentContext context);
	
	/**
	 * Returns a map with whiteboard properties or an empty map
	 * @return a map with whiteboard properties or an empty map
	 */
	public Map<String, Object> getProperties();
	
	/**
	 * Returns the whiteboard name
	 * @return the whiteboard name
	 */
	public String getName();
	
	/**
	 * Registers a new application, that is contained in the application provider
	 * @param provider the application provider to register
	 */
	public void registerApplication(JakartarsApplicationProvider provider);
	
	/**
	 * Unregisters an application, contained in the application provider 
	 * @param provider the application provider to be removed
	 */
	public void unregisterApplication(JakartarsApplicationProvider provider);

	/**
	 * Reloads the application contained in the application provider
	 * @param provider the application to be reloaded
	 */
	public void reloadApplication(JakartarsApplicationProvider provider);
	
	/**
	 * Returns <code>true</code>, if the given application was already registered
	 * @param provider the application provider to check 
	 * @return <code>true</code>, if the given application was already registered
	 */
	public boolean isRegistered(JakartarsApplicationProvider provider);
	



}
