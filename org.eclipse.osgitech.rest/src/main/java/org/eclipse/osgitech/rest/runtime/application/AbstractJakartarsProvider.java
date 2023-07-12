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

import static java.util.Objects.isNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.eclipse.osgitech.rest.provider.JakartarsConstants;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

/**
 * An abstract provider implementation. This provider is intended to have many instances for the same content.
 * As identifier the name should taken to be unique for the content.
 * @author Mark Hoffmann
 * @since 11.10.2017
 */
public abstract class AbstractJakartarsProvider<T> implements JakartarsConstants, Comparable<AbstractJakartarsProvider<?>> {

	private static final Filter IMPOSSIBLE_MATCH;
	
	static {
		try {
			IMPOSSIBLE_MATCH = FrameworkUtil.createFilter("(&(foo=bar)(!(foo=bar)))");
		} catch (InvalidSyntaxException e) {
			throw new IllegalArgumentException("The filter failed to be created", e);
		}
	}
	
	private static final Logger logger = Logger.getLogger("jersey.abstractProvider");
	private final Map<String, Object> properties;
	private String name;
	private String id;
	private Long serviceId;
	private Integer serviceRank;
	private int status = NO_FAILURE;
	private Filter whiteboardTargetFilter;
	private List<Filter> extensionFilters = new LinkedList<>();
	private T providerObject;

	public AbstractJakartarsProvider(T providerObject, Map<String, Object> properties) {
		this.properties = properties == null ? Collections.emptyMap() : properties;
		this.providerObject = providerObject;
		validateProperties();
	}
	
	/** 
	 * The unique identifier for this provider. Should be stable, i.e. if two instances
	 * are created for the same provider they should have the same id.
	 */
	public String getId() {
		return id;
	}

	/** 
	 * A human readable name for the provider
	 */
	public String getName() {
		return name;
	}

	/** 
	 * The service id for the provider. Will be -1 if this instance is not backed
	 * by a service
	 */
	public Long getServiceId() {
		return serviceId;
	}

	/** 
	 * The service ranking for this provider. Will be 0 if this instance has no
	 * service ranking
	 */
	public Integer getServiceRank() {
		return serviceRank;
	}

	/** 
	 * Returns true if this provider has failed validation, or been set with a
	 * failure status
	 */
	public boolean isFailed() {
		return getProviderStatus() != NO_FAILURE;
	}

	/**
	 * Get the properties (usually service properties) associated with this
	 * provider
	 */
	public Map<String, Object> getProviderProperties() {
		return Collections.unmodifiableMap(properties);
	}
	
	/** 
	 * Returns true if this provider requires any extensions using the
	 * <code>osgi.jakartars.extension.select</code> filter
	 */
	public boolean requiresExtensions() {
		return !extensionFilters.isEmpty();
	}

	/** 
	 * Returns true if this provider can be applied to the supplied rest
	 * whiteboard. If a <code>osgi.jakartars.whiteboard.select</code> filter
	 * is present then it will be checked
	 */
	public boolean canHandleWhiteboard(Map<String, Object> runtimeProperties) {
		/* 
		 * Spec table 151.2: osgi.jakartars.whiteboard.target: ... If this property is not specified,
		 * all Jakartars Whiteboards can handle this service
		 */
		if (whiteboardTargetFilter == null) {
			return true;
		}
		runtimeProperties = isNull(runtimeProperties)   ? Collections.emptyMap() : runtimeProperties;
		boolean match = whiteboardTargetFilter.matches(runtimeProperties);

		return match;
	}
	
	/** 
	 * Get the list of extension selection filters for this provider
	 */
	public List<Filter> getExtensionFilters() {
		return extensionFilters;
	}
	
	/** 
	 * Get the provider object that this provider represents
	 */
	public T getProviderObject() {
		return providerObject;
	}

	/**
	 * Returns the internal status of the provider
	 * @return the internal status of the provider
	 */
	protected int getProviderStatus() {
		return status;
	}

	/**
	 * Sets a new provider name
	 * @param name the name to set
	 */
	protected void setProviderName(String name) {
		this.name = name;
	}
	
	/**
	 * Calculates an id for the provider.
	 */
	protected String calculateProviderId() {
		Long serviceId = getServiceId();
		if (isNull(serviceId)) {
			return "." + UUID.randomUUID().toString();
		}
		return "sid_" + serviceId;
	}

	/**
	 * Determine the name for the provider based on the <code>osgi.jakartars.name</code>
	 */
	protected String getProviderName() {
		String providerName = calculateProviderId();
		if (properties != null) {
			String jakartarsName = (String) properties.get(JakartarsWhiteboardConstants.JAKARTA_RS_NAME);
			if (jakartarsName != null) {
				providerName = jakartarsName;
				if (jakartarsName.startsWith("osgi") || jakartarsName.startsWith(".")) {
					updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
				}
			}
		}
		return providerName;
	}

	/**
	 * Validates all properties which are usually the service properties. 
	 * It starts with the name and serviceId and delegates to custom implementations
	 */
	protected void validateProperties() {
		updateStatus(NO_FAILURE);
		serviceId = (Long) properties.get(Constants.SERVICE_ID);
		if (serviceId == null) {
			serviceId = (Long) properties.get(ComponentConstants.COMPONENT_ID);
		}
		id = calculateProviderId();
		name = getProviderName();
		Object sr = properties.get(Constants.SERVICE_RANKING);
		if (sr != null && sr instanceof Integer) {
			serviceRank = (Integer)sr;
		} else {
			serviceRank = Integer.valueOf(0);
		}
		if (serviceId == null) {
			serviceId = (Long) properties.get(ComponentConstants.COMPONENT_ID);
		}
		String filter = (String) properties.get(JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_TARGET);
		if (filter != null) {
			try {
				whiteboardTargetFilter = FrameworkUtil.createFilter(filter);
			} catch (InvalidSyntaxException e) {
				logger.log(Level.SEVERE, "The given whiteboard target filter is invalid: " + filter);
				updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
				whiteboardTargetFilter = IMPOSSIBLE_MATCH;
			}
		}
		String[] filters = JerseyHelper.getStringPlusProperty(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION_SELECT, properties);
		if (filters != null) {
			for (String f : filters) {
				try {
					Filter extensionFilter = FrameworkUtil.createFilter(f);
					extensionFilters.add(extensionFilter);
				} catch (InvalidSyntaxException e) {
					logger.log(Level.SEVERE, "The given extension select filter is invalid: " + filter, e);
					extensionFilters.clear();
					updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
					break;
				}
			}
		}
		extensionFilters = List.copyOf(extensionFilters);
		doValidateProperties(properties);
	}

	/**
	 * Validates the properties
	 * @param properties the properties to validate
	 */
	protected abstract void doValidateProperties(Map<String, Object> properties);

	/**
	 * Updates the status. This is an indicator for creating failed DTO's
	 * @param newStatus the new status to update
	 */
	public void updateStatus(int newStatus) {
		if (newStatus == status) {
			return;
		}
		if (status == NO_FAILURE) {
			status = newStatus;
		} else {
			if (newStatus != status) {
				status = newStatus;
			}
		}
	}
	
	/**
	 * Returns a clean copy of this object as if it had been freshly
	 * constructed. This will clear any error status, but validation
	 * errors will be regenerated.
	 * @return
	 */
	public abstract AbstractJakartarsProvider<T> cleanCopy();
	
	/* 
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public final int compareTo(AbstractJakartarsProvider<?> o) {
		if (o == null) {
			throw new NullPointerException();
		}
		
		Long si = getServiceId();
		
		Long si2 = o.getServiceId();

		// service rank is equal -> same provider
		if(si != null && si.equals(si2)) {
			return 0;
		}
		
		
		Integer sr = getServiceRank();
		sr = sr == null ? 0 : sr;

		Integer sr2 = o.getServiceRank();
		sr2 = sr2 == null ? 0 : sr2;
		
		// Not the same so sort descending by service rank
		int r = sr2.compareTo(sr);
		
		if (r == 0) {
			// Same rank, so look for lowest service id
			if(si != null) {
				if(si2 != null) {
					return si.compareTo(si2);
				} else {
					// Fake services come last
					r = -1;
				}
			} else {
				if(si2 != null) {
					// Fake services come last
					r = 1;
				} else {
					r = getName().compareTo(o.getName());
				}
			}
		}		
		return r;
	}

}
