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

import java.util.concurrent.ExecutorService;

import jakarta.ws.rs.client.RxInvokerProvider;
import jakarta.ws.rs.client.SyncInvoker;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jakartars.client.PromiseRxInvoker;

/**
 * 
 * @author ilenia
 * @since Jun 12, 2020
 */
@Component(service = RxInvokerProvider.class)
public class RxInvokerProviderImpl implements RxInvokerProvider<PromiseRxInvoker> {
	
	/* 
	 * (non-Javadoc)
	 * @see jakarta.ws.rs.client.RxInvokerProvider#isProviderFor(java.lang.Class)
	 */
	@Override
	public synchronized boolean isProviderFor(Class<?> clazz) {
		if(PromiseRxInvoker.class.equals(clazz)) {
			return true;
		}
		return false;
	}

	/* 
	 * (non-Javadoc)
	 * @see jakarta.ws.rs.client.RxInvokerProvider#getRxInvoker(jakarta.ws.rs.client.SyncInvoker, java.util.concurrent.ExecutorService)
	 */
	@Override
	public synchronized PromiseRxInvoker getRxInvoker(SyncInvoker syncInvoker, ExecutorService executorService) {
		return new PromiseRxInvokerImpl(syncInvoker, executorService);		
	}

}
