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

import org.osgi.service.jakartars.runtime.dto.BaseExtensionDTO;

import jakarta.ws.rs.core.FeatureContext;

/**
 * Provider interface for Jakartars extensions
 * @author Mark Hoffmann
 * @since 11.10.2017
 */
public interface JakartarsExtensionProvider extends JakartarsApplicationContentProvider {

	/**
	 * Returns <code>true</code>, if the provider contains a valid extension, otherwise <code>false</code>
	 * @return <code>true</code>, if the provider contains a valid extension, otherwise <code>false</code>
	 */
	public boolean isExtension();
	
	/**
	 * Returns the extension DTO or failed extension DTO
	 * @return the extension DTO or failed extension DTO
	 */
	public BaseExtensionDTO getExtensionDTO();
	
	/**
	 * Returns the contracts under which the Extensions have to be registered. Can be <code>null</code> if
	 * no specific contracts are provided. In this case the JAX-RS implementation has to scan the class for 
	 * Annotations and Interfaces
	 * @return the Array of Classes
	 */
	public Class<?>[] getContracts();
	
	/**
	 * Returns an Extension instance suitable for binding into an application. This extension must be
	 * disposed once it is finished with
	 * @param context the current context for creation
	 * @return the extension
	 */
	public JakartarsExtension getExtension(FeatureContext context);
	
	public interface JakartarsExtension {
		
		/**
		 * Returns the contract priorities for this extension. The keys will match the values returned by
		 * {@link JakartarsExtensionProvider#getContracts()}
		 * @return the Contract priorities
		 */
		public Map<Class<?>, Integer> getContractPriorities();
		
		/**
		 * Get the extension object
		 */
		public Object getExtensionObject();
		
		/**
		 * Release the extension object
		 */
		public void dispose();
	}
	
	@Override
	public JakartarsExtensionProvider cleanCopy();
}
