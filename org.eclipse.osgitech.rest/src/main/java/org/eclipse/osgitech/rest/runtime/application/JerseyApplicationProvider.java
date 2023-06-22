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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Application;

import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.helper.JakartarsHelper;
import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.eclipse.osgitech.rest.provider.application.AbstractJakartarsProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationContentProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider;
import org.glassfish.jersey.servlet.ServletContainer;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * Implementation of the Application Provider
 * @author Mark Hoffmann
 * @since 30.07.2017
 */
public class JerseyApplicationProvider extends AbstractJakartarsProvider<Application> implements JakartarsApplicationProvider {

	private static final Logger logger = Logger.getLogger("jersey.applicationProvider");
	private List<ServletContainer> applicationContainers = new LinkedList<>();
	private String applicationBase;
	private boolean changed = true;
	private JerseyApplication wrappedApplication = null;

	public JerseyApplicationProvider(Application application, Map<String, Object> properties) {
		super(application, properties);
		// create name after validation, because some fields are needed eventually
		if(application != null) {
			wrappedApplication = new JerseyApplication(getProviderName(), application, properties);
		}
		validateProperties();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#addServletContainer(org.glassfish.jersey.servlet.ServletContainer)
	 */
	@Override
	public void addServletContainer(ServletContainer applicationContainer) {
		applicationContainers.add(applicationContainer);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#removeServletContainer(org.glassfish.jersey.servlet.ServletContainer)
	 */
	@Override
	public void removeServletContainer(ServletContainer applicationContainer) {
		applicationContainers.remove(applicationContainer);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getServletContainers()
	 */
	@Override
	public List<ServletContainer> getServletContainers() {
		return applicationContainers;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getPath()
	 */
	@Override
	public String getPath() {
		if (wrappedApplication == null) {
			throw new IllegalStateException("This application provider does not contain an application, but should have one to create a context path");
		}
		return applicationBase == null ? null : JakartarsHelper.getServletPath(wrappedApplication.getSourceApplication() , applicationBase);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getJakartarsApplication()
	 */
	@Override
	public Application getJakartarsApplication() {
		if (wrappedApplication == null) {
			throw new IllegalStateException("This application provider does not contain an application, but should have one to return an application");
		}
		return wrappedApplication;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getApplicationProperties()
	 */
	@Override
	public Map<String, Object> getApplicationProperties() {
		return getProviderProperties();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getApplicationDTO()
	 */
	@Override
	public BaseApplicationDTO getApplicationDTO() {
		int status = getProviderStatus();
		if (wrappedApplication == null) {
			throw new IllegalStateException("This application provider does not contain an application, but should have one to get a DTO");
		}
		if (status == NO_FAILURE) {
			return DTOConverter.toApplicationDTO(this);
		} else {
			return DTOConverter.toFailedApplicationDTO(this, status);
		}
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#isDefault()
	 */
	public boolean isDefault() {
		return JakartarsWhiteboardConstants.JAKARTA_RS_DEFAULT_APPLICATION.equals(getName());
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#isShadowDefault()
	 */
	@Override
	public boolean isShadowDefault() {
		return "/".equals(applicationBase) && !isDefault();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return JerseyHelper.isEmpty(wrappedApplication);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#isChanged()
	 */
	@Override
	public boolean isChanged() {
		return changed;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#markUnchanged()
	 */
	@Override
	public void markUnchanged() {
		changed = false;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#addResource(org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider)
	 */
	@Override
	public boolean addResource(JakartarsResourceProvider provider) {
		if (!provider.isResource()) {
			logger.log(Level.WARNING, "The resource to add is not declared with the resource property: " + JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE);
			return false;
		}
		return doAddContent(provider);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#removeResource(org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider)
	 */
	@Override
	public boolean removeResource(JakartarsResourceProvider provider) {
		if (provider == null) {
			logger.log(Level.WARNING, "The resource provider is null. There is nothing to remove.");
			return false;
		}
		if (!provider.isResource()) {
			logger.log(Level.WARNING, "The resource to be removed is not declared with the resource property: " + JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE);
			return false;
		}
		return doRemoveContent(provider);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#addExtension(org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider)
	 */
	@Override
	public boolean addExtension(JakartarsExtensionProvider provider) {
		if (!provider.isExtension()) {
			logger.log(Level.WARNING, "The extension to add is not declared with the extension property: " + JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION);
			return false;
		}
		return doAddContent(provider);
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#removeExtension(org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider)
	 */
	@Override
	public boolean removeExtension(JakartarsExtensionProvider provider) {
		if (provider == null) {
			logger.log(Level.WARNING, "The extension provider is null. There is nothing to remove.");
			return false;
		}
		if (!provider.isExtension()) {
			logger.log(Level.WARNING, "The extension to be removed is not declared with the extension property: " + JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION);
			return false;
		}
		return doRemoveContent(provider);
	}
	
	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		return new JerseyApplicationProvider(getProviderObject(), getProviderProperties());
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
			String baseProperty = (String) providerProperties.get(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE);
			if (wrappedApplication != null) {
				baseProperty = getPath();
			}
			name = (String) providerProperties.get(JakartarsWhiteboardConstants.JAKARTA_RS_NAME);
			if (name == null && baseProperty != null) {
				name = "." + baseProperty;
			} else if (name != null && !name.equals(".default") && (name.startsWith(".") || name.startsWith("osgi"))) {
				updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			}
		}
		return name == null ? getProviderId() : name;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.AbstractJakartarsProvider#doValidateProperties(java.util.Map)
	 */
	@Override
	protected void doValidateProperties(Map<String, Object> properties) {
		String baseProperty = (String) properties.get(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE);
		if (applicationBase == null && (baseProperty == null || baseProperty.isEmpty())) {
			updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			return;
		}
		if (baseProperty != null && !baseProperty.isEmpty()) {
			applicationBase = baseProperty;
		} 
	}

	/**
	 * Adds content to the underlying {@link JerseyApplication}, if valid
	 * @param provider the content provider to be added
	 * @return <code>true</code>, if add was successful, otherwise <code>false</code>
	 */
	private boolean doAddContent(JakartarsApplicationContentProvider provider) {
		if(getApplicationDTO() instanceof FailedApplicationDTO) {
			return false;
		}
		boolean filterValid = true; 
		if (filterValid) {
			JerseyApplication jerseyApplication = wrappedApplication;
			boolean added = jerseyApplication.addContent(provider);
			if (!changed && added) {
				changed = added;
			}
		}
		return filterValid;
	}
	
	/**
	 * Removed content from the underlying {@link JerseyApplication}, if valid
	 * @param provider the content provider to be removed
	 * @return <code>true</code>, if removal was successful, otherwise <code>false</code>
	 */
	private boolean doRemoveContent(JakartarsApplicationContentProvider provider) {
		boolean removed = wrappedApplication.removeContent(provider);
		if (!changed && removed) {
			changed = removed;
		}
		return removed;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getContentProviers()
	 */
	@Override
	public Collection<JakartarsApplicationContentProvider> getContentProviers() {
		return wrappedApplication.getContentProviders();
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#updateApplicationBase(java.lang.String)
	 */
	public void updateApplicationBase(String applicationBase) {
		doValidateProperties(Collections.singletonMap(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, applicationBase));
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.runtime.servlet.DestroyListener#servletContainerDestroyed(org.glassfish.jersey.servlet.ServletContainer)
	 */
	@Override
	public void servletContainerDestroyed(ServletContainer container) {
		applicationContainers.remove(container);
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
