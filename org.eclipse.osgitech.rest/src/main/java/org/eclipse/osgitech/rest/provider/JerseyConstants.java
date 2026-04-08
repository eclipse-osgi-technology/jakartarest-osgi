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
package org.eclipse.osgitech.rest.provider;

import org.osgi.service.condition.Condition;

/**
 * Interface for constants used in Jersey
 * @author Mark Hoffmann
 * @since 13.07.2017
 */
public interface JerseyConstants {
	
	public static final String JERSEY_RUNTIME = "jersey.runtime";
	public static final String JERSEY_CLIENT = "jersey.client";
	public static final String JERSEY_CLIENT_ONLY = "jersey.clientOnly";
	public static final String JERSEY_RUNTIME_CONDITION = "(" + Condition.CONDITION_ID + "=" + JerseyConstants.JERSEY_RUNTIME + ")";
	public static final String JERSEY_CLIENT_CONDITION = "(|(" + Condition.CONDITION_ID + "=" + JerseyConstants.JERSEY_CLIENT + ")" + JERSEY_RUNTIME_CONDITION + ")";
	public static final String JERSEY_SCHEMA = "jersey.schema";
	public static final String JERSEY_HOST = "jersey.host";
	public static final String JERSEY_PORT = "jersey.port";
	public static final String JERSEY_CONTEXT_PATH = "jersey.context.path";
	public static final String JERSEY_WHITEBOARD_NAME = "jersey.jakartars.whiteboard.name";
	public static final String JERSEY_STRICT_MODE = "jersey.jakartars.whiteboard.strict";
	public static final String JERSEY_DISABLE_SESSION = "jersey.disable.sessions";
	
	public static final Integer WHITEBOARD_DEFAULT_PORT = Integer.valueOf(8181);
	public static final String WHITEBOARD_DEFAULT_CONTEXT_PATH = "/rest";
	public static final String WHITEBOARD_DEFAULT_HOST = "localhost";
	public static final String WHITEBOARD_DEFAULT_SCHEMA = "http";
	public static final String WHITEBOARD_DEFAULT_NAME = "Jersey REST";

}
