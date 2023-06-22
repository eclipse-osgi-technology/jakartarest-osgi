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
package org.eclipse.osgitech.rest.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

import jakarta.ws.rs.core.Application;

/**
 * Helper class for the Jersey whiteboard
 * @author Mark Hoffmann
 * @since 16.07.2017
 */
public class JerseyHelper {

	/**
	 * Returns the property. If it not available but a default value is set, the
	 * default value will be returned.
	 * @param context the component context
	 * @param key the properties key
	 * @param defaultValue the default value
	 * @return the value or defaultValue or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getPropertyWithDefault(ComponentContext context, String key, T defaultValue) {
		if (context == null) {
			throw new IllegalStateException("Cannot call getProperties in a state, where the component context is not available");
		}
		Object value = context.getProperties().get(key);
		return value == null ? defaultValue : (T)value;
	}

	/**
	 * Returns <code>true</code>, if the application does not contain any resources or extensions
	 * @param application the application to check
	 * @return s <code>true</code>, if the application does not contain any resources or extensions
	 */
	public static boolean isEmpty(Application application) {
		if (application == null) {
			return true;
		}
		return application.getClasses().isEmpty() && 
				application.getSingletons().isEmpty();

	}
	
	/**
	 * Returns a string+ property with the given property name from the given property map
	 * @param propertyName the property name
	 * @param properties the property map
	 * @return the array of values for the property name or <code>null</code>
	 */
	public static String[] getStringPlusProperty(String propertyName, Map<String, Object> properties) {
		if (propertyName == null || properties == null) {
			return null;
		}
		Object filterObject = properties.get(propertyName);
		String[] filters = null;
		if (filterObject instanceof String) {
			filters = new String[] {filterObject.toString()};
		} else if (filterObject instanceof String[]) {
			filters = (String[])filterObject;
		} else if (filterObject instanceof List) {
			filters = ((List<?>) filterObject).stream()
					.map(String::valueOf)
					.toArray(String[]::new);
		}
		return filters;
	}
	
	/**
	 * Creates a properties map from the service reference properties
	 * @param reference the service reference
	 * @return a properties map
	 */
	public static Map<String, Object> getServiceProperties(ServiceReference<?> reference) {
		Map<String, Object> props = new HashMap<>();
		if (reference != null) {
			for (String key : reference.getPropertyKeys()) {
				props.put(key,  reference.getProperty(key));
			}
		}
		return props;
	}

}
