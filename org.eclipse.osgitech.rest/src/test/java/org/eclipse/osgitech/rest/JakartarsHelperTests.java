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
package org.eclipse.osgitech.rest;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.osgitech.rest.helper.JakartarsHelper;
import org.junit.jupiter.api.Test;

/**
 * 
 * @author Mark Hoffmann
 * @since 21.09.2017
 */
public class JakartarsHelperTests {

	@Test
	public void testToServletPath() {
		assertEquals("/*", JakartarsHelper.toServletPath(null));
		assertEquals("/*", JakartarsHelper.toServletPath(""));
		assertEquals("/*", JakartarsHelper.toServletPath("/"));
		assertEquals("/test/*", JakartarsHelper.toServletPath("/test"));
		assertEquals("/test/*", JakartarsHelper.toServletPath("test"));
		assertEquals("/test/*", JakartarsHelper.toServletPath("/test/*"));
		assertEquals("/test/*", JakartarsHelper.toServletPath("test/*"));
	}
	
	@Test
	public void testToApplicationPath() {
		assertEquals("*", JakartarsHelper.toApplicationPath(null));
		assertEquals("*", JakartarsHelper.toApplicationPath(""));
		assertEquals("*", JakartarsHelper.toApplicationPath("/"));
		assertEquals("test/*", JakartarsHelper.toApplicationPath("/test"));
		assertEquals("test/*", JakartarsHelper.toApplicationPath("test"));
		assertEquals("test/*", JakartarsHelper.toApplicationPath("/test/*"));
		assertEquals("test/*", JakartarsHelper.toApplicationPath("test/*"));
	}

}
