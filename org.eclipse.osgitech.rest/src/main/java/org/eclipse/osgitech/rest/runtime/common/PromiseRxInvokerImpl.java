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

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.SyncInvoker;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;

import org.osgi.service.jakartars.client.PromiseRxInvoker;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;

/**
 * 
 * @author ilenia
 * @since Jun 12, 2020
 */
public class PromiseRxInvokerImpl implements PromiseRxInvoker {
	
	
	private SyncInvoker syncInvoker;
	private PromiseFactory factory;
	
	public PromiseRxInvokerImpl(SyncInvoker syncInvoker, ExecutorService executorService) {
		this.syncInvoker = syncInvoker;		

        if (executorService != null) {
        	factory = new PromiseFactory(executorService);
        }
        else {
        	factory = new PromiseFactory(
                PromiseFactory.inlineExecutor());
        }				
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#delete()
	 */
	@Override
	public Promise<Response> delete() {		
		return factory.submit(() -> {
			Response response = syncInvoker.delete();
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#delete(java.lang.Class)
	 */
	@Override
	public <R> Promise<R> delete(Class<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.delete(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#delete(jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> delete(GenericType<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.delete(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#get()
	 */
	@Override
	public Promise<Response> get() {
		return factory.submit(() -> {
			Response response = syncInvoker.get();
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#get(java.lang.Class)
	 */
	@Override
	public <R> Promise<R> get(Class<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.get(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#get(jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> get(GenericType<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.get(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#head()
	 */
	@Override
	public Promise<Response> head() {
		return factory.submit(() -> {
			Response response = syncInvoker.head();
			return response;			
		});
	}

//	@Override
//    public <R> Promise<R> method(String s, Class<R> responseType) {
//        
//            Deferred<R> deferred = factory.deferred();
//            
//            Builder builder  = (Builder) syncInvoker;
//            deferred.
//            builder.async().method(s, responseType);
//
//        
//        return deferred.getPromise();
//    }
//	
	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#method(java.lang.String, java.lang.Class)
	 */
	@Override
	public <R> Promise<R> method(String arg0, Class<R> arg1) {
		return factory.submit(() -> {
			R response = syncInvoker.method(arg0, arg1);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#method(java.lang.String, jakarta.ws.rs.client.Entity, java.lang.Class)
	 */
	@Override
	public <R> Promise<R> method(String arg0, Entity<?> arg1, Class<R> arg2) {
		return factory.submit(() -> {
			R response = syncInvoker.method(arg0, arg1, arg2);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#method(java.lang.String, jakarta.ws.rs.client.Entity, jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> method(String arg0, Entity<?> arg1, GenericType<R> arg2) {
		return factory.submit(() -> {
			R response = syncInvoker.method(arg0, arg1, arg2);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#method(java.lang.String, jakarta.ws.rs.client.Entity)
	 */
	@Override
	public Promise<Response> method(String arg0, Entity<?> arg1) {
		return factory.submit(() -> {
			Response response = syncInvoker.method(arg0, arg1);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#method(java.lang.String, jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> method(String arg0, GenericType<R> arg1) {
		return factory.submit(() -> {
			R response = syncInvoker.method(arg0, arg1);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#method(java.lang.String)
	 */
	@Override
	public Promise<Response> method(String arg0) {
		return factory.submit(() -> {
			Response response = syncInvoker.method(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#options()
	 */
	@Override
	public Promise<Response> options() {
		return factory.submit(() -> {
			Response response = syncInvoker.options();
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#options(java.lang.Class)
	 */
	@Override
	public <R> Promise<R> options(Class<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.options(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#options(jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> options(GenericType<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.options(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#post(jakarta.ws.rs.client.Entity, java.lang.Class)
	 */
	@Override
	public <R> Promise<R> post(Entity<?> arg0, Class<R> arg1) {
		return factory.submit(() -> {
			R response = syncInvoker.post(arg0, arg1);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#post(jakarta.ws.rs.client.Entity, jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> post(Entity<?> arg0, GenericType<R> arg1) {
		return factory.submit(() -> {
			R response = syncInvoker.post(arg0, arg1);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#post(jakarta.ws.rs.client.Entity)
	 */
	@Override
	public Promise<Response> post(Entity<?> arg0) {
		return factory.submit(() -> {
			Response response = syncInvoker.post(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#put(jakarta.ws.rs.client.Entity, java.lang.Class)
	 */
	@Override
	public <R> Promise<R> put(Entity<?> arg0, Class<R> arg1) {
		return factory.submit(() -> {
			R response = syncInvoker.put(arg0, arg1);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#put(jakarta.ws.rs.client.Entity, jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> put(Entity<?> arg0, GenericType<R> arg1) {
		return factory.submit(() -> {
			R response = syncInvoker.put(arg0, arg1);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#put(jakarta.ws.rs.client.Entity)
	 */
	@Override
	public Promise<Response> put(Entity<?> arg0) {
		return factory.submit(() -> {
			Response response = syncInvoker.put(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#trace()
	 */
	@Override
	public Promise<Response> trace() {
		return factory.submit(() -> {
			Response response = syncInvoker.trace();
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#trace(java.lang.Class)
	 */
	@Override
	public <R> Promise<R> trace(Class<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.trace(arg0);
			return response;			
		});
	}

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.client.PromiseRxInvoker#trace(jakarta.ws.rs.core.GenericType)
	 */
	@Override
	public <R> Promise<R> trace(GenericType<R> arg0) {
		return factory.submit(() -> {
			R response = syncInvoker.trace(arg0);
			return response;			
		});
	}

}
