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

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Helper class for Jakartars related stuff
 * @author Mark Hoffmann
 * @since 12.07.2017
 */
public class JakartarsHelper {

	public static String getFullApplicationPath(Application application, String applicationBase) {
		String strippedBase = stripApplicationPath(applicationBase);
		if (application != null) {
			ApplicationPath applicationPathAnnotation = application.getClass().getAnnotation(ApplicationPath.class);
			if (applicationPathAnnotation != null) {
				return strippedBase + stripApplicationPath(applicationPathAnnotation.value());
			}
		}
		return strippedBase;
	}
	
	/**
	 * Returns a servlet registration path from the given application. For that, the {@link ApplicationPath} annotation
	 * will be read. If present the value is taken and transformed into a valid Servlet spec format with
	 * leading '/' and trailing /* to make the resources work.
	 * If no application instance id given the default value /* is returned.
	 * @param application the Jakartars application instance
	 * @return the application path
	 */
	public static String getServletPath(Application application, String applicationBase) {
		if (application != null) {
			ApplicationPath applicationPathAnnotation = application.getClass().getAnnotation(ApplicationPath.class);
			if (applicationPathAnnotation != null) {
				String applicationPath = applicationPathAnnotation.value();
				String stripedApplicationBase = stripApplicationPath(applicationBase);
				return stripedApplicationBase + toServletPath(applicationPath);
			}
		}
		return toServletPath(applicationBase);
	}

	private static String stripApplicationPath(String applicationPath) {
		String resultPath =  applicationPath.startsWith("/") ? "" : "/";
		if(applicationPath.endsWith("/*")) {
			resultPath += applicationPath.substring(0, applicationPath.length() - 2);
		} else if(applicationPath.endsWith("/")) {
			resultPath += applicationPath.substring(0, applicationPath.length() - 1);
		} else {
			resultPath += applicationPath;
		}
		return resultPath;
	}

	/**
	 * Returns a servlet registration path from the given path. If the path value is <code>null</code>,
	 * the default /* is returned. If present the value is taken and transformed into a valid Servlet spec format with
	 * leading '/' and trailing /* to make the resources work.
	 * If no application instance id given the default value /* is returned.
	 * @param application the Jakartars application instance
	 * @return the application path
	 */
	public static String toServletPath(String path) {
		return "/" + toApplicationPath(path);
	}
	
	/**
	 * Returns a servlet registration path from the given path. If the path value is <code>null</code>,
	 * the default /* is returned. If present the value is taken and transformed into a valid Servlet spec format with
	 * leading '/' and trailing /* to make the resources work.
	 * If no application instance id given the default value /* is returned.
	 * @param application the Jakartars application instance
	 * @return the application path
	 */
	public static String toApplicationPath(String path) {
		String applicationPath = "*";
		if (path == null || path.isEmpty() || path.equals("/")) {
			return applicationPath;
		}
		applicationPath = path;
		if (applicationPath != null && !applicationPath.isEmpty()) {
			if (applicationPath.startsWith("/")) {
				applicationPath = applicationPath.substring(1, applicationPath.length());
			}
			if (!applicationPath.endsWith("/") && !applicationPath.endsWith("/*")) {
				applicationPath += "/*";
				
			}
			if (applicationPath.endsWith("/")) {
				applicationPath += "*";
			}
		}
		return applicationPath;
	}

}
