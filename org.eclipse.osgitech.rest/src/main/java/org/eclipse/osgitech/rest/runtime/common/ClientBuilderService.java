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

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.RxInvokerProvider;

import org.glassfish.jersey.client.JerseyClientBuilder;
import org.osgi.service.jakartars.client.PromiseRxInvoker;

import aQute.bnd.annotation.spi.ServiceProvider;


/**
 * A simple class to enable DS to pickup on the Jersey Client Builder
 * @author Juergen Albert
 * @since 27 Jul 2018
 */
@ServiceProvider(value = ClientBuilder.class)
public class ClientBuilderService extends JerseyClientBuilder {
	
	/**
	 * Creates a new instance.
	 */
	public ClientBuilderService(RxInvokerProvider<PromiseRxInvoker> provider) {
		register(provider);
	}

}
