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
package org.eclipse.osgitech.rest.runtime.common;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.sse.SseEventSource;
import jakarta.ws.rs.sse.SseEventSource.Builder;

import org.osgi.service.jakartars.client.SseEventSourceFactory;

/**
 * 
 * @author ilenia
 * @since Jun 11, 2020
 */
public class SseEventSourceFactoryImpl implements SseEventSourceFactory {

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.SseEventSourceFactory#newBuilder(jakarta.ws.rs.client.WebTarget)
	 */
	@Override
	public Builder newBuilder(WebTarget target) {		
		return new SseSourceBuilderService().target(target);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.SseEventSourceFactory#newSource(jakarta.ws.rs.client.WebTarget)
	 */
	@Override
	public SseEventSource newSource(WebTarget target) {
		return newBuilder(target).build();
	}

}
