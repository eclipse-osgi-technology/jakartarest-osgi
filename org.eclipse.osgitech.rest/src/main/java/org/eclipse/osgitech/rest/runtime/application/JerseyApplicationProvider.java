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

import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_DEFAULT_APPLICATION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.helper.JakartarsHelper;
import org.eclipse.osgitech.rest.proxy.ApplicationProxyFactory;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Implementation of the Application Provider
 * @author Mark Hoffmann
 * @since 30.07.2017
 */
public class JerseyApplicationProvider extends AbstractJakartarsProvider<Application> {

	private static final Logger logger = Logger.getLogger("jersey.applicationProvider");
	
	private JerseyApplication wrappedApplication;
	private boolean locked;
	private List<JerseyApplicationContentProvider> providers = new ArrayList<>();

	public JerseyApplicationProvider(Application application, Map<String, Object> properties) {
		super(application, properties);
	}
	
	/** 
	 * Gets the full application path, including the osgi.jakartars.application.base and any
	 * {@link ApplicationPath} that is applied to the application
	 */
	public String getPath() {
		String base = getApplicationBase();
		return JakartarsHelper.getFullApplicationPath(getProviderObject(), base == null ? "" : base);
	}
	
	/** 
	 * Gets the wrapped whiteboard application suitable for deployment into Jersey
	 */
	public Application getJakartarsApplication() {
		locked = true;
		if (wrappedApplication == null) {
			Application application = getProviderObject();
			if (application.getClass().isAnnotationPresent(ApplicationPath.class)) {
				// Dynamic subclass with the annotation value
				wrappedApplication = ApplicationProxyFactory.createDynamicSubclass(getApplicationBase(), application, 
						getProviderProperties(), providers);
			} else {
				wrappedApplication = new JerseyApplication(getProviderName(), application, getProviderProperties(), providers);
			}
		}
		return wrappedApplication;
	}

	/** 
	 * Get the DTO representing this provider
	 */
	public BaseApplicationDTO getApplicationDTO() {
		int status = getProviderStatus();
		if (status == NO_FAILURE) {
			return DTOConverter.toApplicationDTO(this);
		} else {
			return DTOConverter.toFailedApplicationDTO(this, status);
		}
	}

	/**
	 * Returns true if this is the default application
	 */
	public boolean isDefault() {
		return JAKARTA_RS_DEFAULT_APPLICATION.equals(getName());
	}
	
	/** 
	 * Returns true if this is shadowing the default application
	 */
	public boolean isShadowDefault() {
		return "/".equals(getApplicationBase()) && !isDefault();
	}

	/** 
	 * Add a resource provider to this application so that it can be used
	 */
	public boolean addContent(JerseyApplicationContentProvider provider) {
		if (locked) {
			throw new IllegalStateException("The application " + getId() + " (" + getName() + ") is locked");
		}
		if (provider.isFailed()) {
			logger.log(Level.WARNING, "The resource to add is not valid: " + provider.getProviderStatus());
			return false;
		}
		return providers.add(provider);
	}

	/** 
	 * Remove a resource from this application, used as part of validating extension selection
	 */
	public boolean removeContent(JerseyApplicationContentProvider provider) {
		if (locked) {
			throw new IllegalStateException("The application " + getId() + " (" + getName() + ") is locked");
		}
		if (provider == null) {
			logger.log(Level.WARNING, "The resource provider is null. There is nothing to remove.");
			return false;
		}
		return providers.remove(provider);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.AbstractJakartarsProvider#getProviderName()
	 */
	@Override
	protected String getProviderName() {
		String name = null;
		Map<String, Object> providerProperties = getProviderProperties();
		if (providerProperties != null) {
			String baseProperty = getPath();
			name = (String) providerProperties.get(JAKARTA_RS_NAME);
			if (name == null && baseProperty != null) {
				name = "." + baseProperty;
			} else if (name != null && !name.equals(".default") && (name.startsWith(".") || name.startsWith("osgi"))) {
				updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			}
		}
		return name == null ? calculateProviderId() : name;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.AbstractJakartarsProvider#doValidateProperties(java.util.Map)
	 */
	@Override
	protected void doValidateProperties(Map<String, Object> properties) {
		String applicationBase = getApplicationBase();
		if (applicationBase == null || applicationBase.isEmpty()) {
			updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			return;
		}
	}

	private String getApplicationBase() {
		return (String) getProviderProperties().get(JAKARTA_RS_APPLICATION_BASE);
	}
	
	/**
	 * Return the content providers known to this application
	 */
	public Collection<JerseyApplicationContentProvider> getContentProviders() {
		return List.copyOf(providers);
	}

	/**
	 * Return true if t
	 * @param application
	 * @return
	 */
	public boolean isChanged(Application application) {
		return true;
	}

	@Override
	public JerseyApplicationProvider cleanCopy() {
		return new JerseyApplicationProvider(getProviderObject(), getProviderProperties());
	}

}
