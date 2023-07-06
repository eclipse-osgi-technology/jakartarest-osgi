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

import org.osgi.service.jakartars.runtime.dto.BaseDTO;
import org.osgi.service.jakartars.runtime.dto.FailedResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;

/**
 * Provider interface for Jakartars resources
 * @author Mark Hoffmann
 * @since 09.10.2017
 */
public interface JakartarsResourceProvider extends JakartarsApplicationContentProvider {
	
	/**
	 * Returns <code>true</code>, if the given resource is valid and contains the resource properties
	 * @return <code>true</code>, if the given resource is valid and contains the resource properties
	 */
	public boolean isResource();
	
	/**
	 * Returns the {@link ResourceDTO} or {@link FailedResourceDTO} as {@link BaseDTO} for this JakartarsResource.
	 * In case of an error a {@link FailedResourceDTO} instance will be returned
	 * @return the {@link ResourceDTO} or {@link FailedResourceDTO} for this JakartarsResource
	 */
	public BaseDTO getResourceDTO();
	
	@Override
	public JakartarsResourceProvider cleanCopy();
	
}
