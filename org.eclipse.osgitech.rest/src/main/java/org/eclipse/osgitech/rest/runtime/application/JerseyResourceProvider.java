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

import java.util.Map;

import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider;
import org.osgi.framework.ServiceObjects;
import org.osgi.service.jakartars.runtime.dto.BaseDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * A wrapper class for a Jakartars resources 
 * @author Mark Hoffmann
 * @param <T>
 * @since 09.10.2017
 */
public class JerseyResourceProvider<T extends Object> extends JerseyApplicationContentProvider<T> implements JakartarsResourceProvider {

	public JerseyResourceProvider(ServiceObjects<T> serviceObjects, Map<String, Object> properties) {
		super(serviceObjects, properties);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.JakartarsResourceProvider#isResource()
	 */
	@Override
	public boolean isResource() {
		return getProviderStatus() != INVALID;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.JakartarsResourceProvider#getResourceDTO()
	 */
	@Override
	public BaseDTO getResourceDTO() {
		int status = getProviderStatus();
		if (status == NO_FAILURE) {
			return DTOConverter.toResourceDTO(this);
		} else {
			return DTOConverter.toFailedResourceDTO(this, status == INVALID ? DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE : status);
		}
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public JakartarsResourceProvider cleanCopy() {
		return new JerseyResourceProvider<T>(getProviderObject(), getProviderProperties());
	}
	
	/**
	 * Returns the {@link JakartarsWhiteboardConstants} for this resource type 
	 * @return the {@link JakartarsWhiteboardConstants} for this resource type
	 */
	protected String getJakartarsResourceConstant() {
		return JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE;
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.AbstractJakartarsProvider#updateStatus(int)
	 */
	@Override
	public void updateStatus(int newStatus) {
		super.updateStatus(newStatus);
	}

}
