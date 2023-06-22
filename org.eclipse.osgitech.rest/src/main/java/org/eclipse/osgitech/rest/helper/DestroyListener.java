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

import org.glassfish.jersey.servlet.ServletContainer;

/**
 * 
 * @author jalbert
 * @since 23 Aug 2018
 */
public interface DestroyListener {

	public void servletContainerDestroyed(ServletContainer container);
	
}
