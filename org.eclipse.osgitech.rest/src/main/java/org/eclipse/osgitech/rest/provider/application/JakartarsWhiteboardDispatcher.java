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
package org.eclipse.osgitech.rest.provider.application;

import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.core.Application;

import org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;

/**
 * Dispatcher that handles the dynamic adding and removing of resources, extension and applications, that are
 * then delegated to the {@link JakartarsWhiteboardProvider}.
 * Only if the dispatch is active, the delegation to whiteboard provider is enabled
 * @author Mark Hoffmann
 * @since 12.10.2017
 */
public interface JakartarsWhiteboardDispatcher {
	
	/**
	 * #returns the batch mode
	 * @return the batch mode
	 */
	public boolean getBatchMode();
	
	/**
	 * Sets the batchMode
	 * @param batchMode <code>true</code> to handle dispatch manually
	 */
	public void setBatchMode(boolean batchMode);
	/**
	 * executes an manual dispatch, usually used if batchMode == true
	 */
	public void batchDispatch();
	
	/**
	 * Sets a whiteboard instance
	 * @param whiteboard the whiteboard to set
	 */
	public void setWhiteboardProvider(JakartarsWhiteboardProvider whiteboard);
	
	/**
	 * Returns the whiteboard provider instance
	 * @return the whiteboard provider instance
	 */
	public JakartarsWhiteboardProvider getWhiteboardProvider();
	
	/**
	 * Returns all applications or an empty set
	 * @return all applications or an empty set
	 */
	public Set<JakartarsApplicationProvider> getApplications();
	
	/**
	 * Returns all resources or an empty set
	 * @return all resources or an empty set
	 */
	public Set<JakartarsResourceProvider> getResources();
	
	/**
	 * Returns all extensions or an empty set
	 * @return all extensions or an empty set
	 */
	public Set<JakartarsExtensionProvider> getExtensions();
	
	/**
	 * Adds an application
	 * @param application the {@link Application}
	 * @param properties the service properties
	 */
	public void addApplication(Application application, Map<String, Object> properties);
	
	/**
	 * Removes an application
	 * @param application the {@link Application}
	 * @param properties the service properties
	 */
	public void removeApplication(Application application, Map<String, Object> properties);
	
	/**
	 * Adds a resource
	 * @param ref the {@link ServiceReference} of the Resource
	 */
	public void addResource(ServiceObjects<?> appServiceObject, Map<String, Object> properties);
	
	/**
	 * Removes a resource
	 * @param ref the {@link ServiceReference} of the Resource
	 */
	public void removeResource(Map<String, Object> properties);
	
	/**
	 * Adds an extension
	 * @param ref the {@link ServiceReference} of the Extension
	 */
	public void addExtension(ServiceObjects<?> appServiceObject, Map<String, Object> properties);

	/**
	 * Removes an extension
	 * @param ref the {@link ServiceReference} of the Resource
	 */
	public void removeExtension(Map<String, Object> properties);
	
	/**
	 * Activates dispatching
	 */
	public void dispatch();
	
	/**
	 * Deactivates dispatching
	 */
	public void deactivate();
	
	/**
	 * Returns <code>true</code>, if the dispatcher is currently working
	 * @return <code>true</code>, if the dispatcher is currently working
	 */
	public boolean isDispatching();

}
