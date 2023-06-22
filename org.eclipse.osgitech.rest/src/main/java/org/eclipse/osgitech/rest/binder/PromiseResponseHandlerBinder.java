/**
 * Copyright (c) 2012 - 2022 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Tim Ward - initial implementation
 */
package org.eclipse.osgitech.rest.binder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.spi.internal.ResourceMethodInvocationHandlerProvider;

/**
 * OSGi injection binder for HK2, that is used in Jersey. This binder is responsible for
 * the creation of a handler for promise return values
 * @author Tim Ward
 * @since 12.04.2022
 */
public class PromiseResponseHandlerBinder extends AbstractBinder {

	/* (non-Javadoc)
	 * @see org.glassfish.hk2.utilities.binding.AbstractBinder#configure()
	 */
	@Override
	protected void configure() {
		bind(new PromiseResourceMethodInvocationHandlerProvider())
			.to(ResourceMethodInvocationHandlerProvider.class);
	}
}

class PromiseResourceMethodInvocationHandlerProvider implements ResourceMethodInvocationHandlerProvider {

	private final Map<Class<?>, InvocationHandler> cachedHandlers = new ConcurrentHashMap<>();
	
	/* 
	 * (non-Javadoc)
	 * @see org.glassfish.jersey.server.spi.internal.ResourceMethodInvocationHandlerProvider#create(org.glassfish.jersey.server.model.Invocable)
	 */
	@Override
	public InvocationHandler create(Invocable method) {
		Class<?> rawResponseType = method.getRawResponseType();
		if("org.osgi.util.promise.Promise".equals(rawResponseType.getName())) {
			return cachedHandlers.computeIfAbsent(rawResponseType, PromiseResourceMethodInvocationHandler::new);
		}
		return null;
	}

	
	static class PromiseResourceMethodInvocationHandler implements InvocationHandler {
		
		private final Class<?> promiseClass;

		private final Method register;
		private final Method getFailure;
		private final Method getValue;
		private final Method isDone;
		
		/**
		 * Creates a new handler for this promise type.
		 * @throws SecurityException 
		 * @throws NoSuchMethodException 
		 */
		public PromiseResourceMethodInvocationHandler(Class<?> promiseClass) {
			this.promiseClass = promiseClass;
			try {
				register = promiseClass.getMethod("onResolve", Runnable.class);
				getFailure = promiseClass.getMethod("getFailure");
				getValue = promiseClass.getMethod("getValue");
				isDone = promiseClass.getMethod("isDone");
			} catch (Exception e) {
				throw new RuntimeException("Failed to set up InvocationHandler for Class " + promiseClass, e);
			}
		}

		/* 
		 * (non-Javadoc)
		 * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
		 */
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Object result = method.invoke(proxy, args);
			if(result != null) {
				if(promiseClass.isInstance(result)) {
					
					if((Boolean) isDone.invoke(result)) {
						Throwable t = (Throwable) getFailure.invoke(result);
						if(t != null) {
							throw t;
						} else {
							result = getValue.invoke(t);
						}
					} else {
						result = handleAsync(result);
					}
					
				} else {
					// TODO log that we got the wrong type of result
				}
			}
			return result;
		}

		/**
		 * @param result
		 * @throws IllegalAccessException
		 * @throws InvocationTargetException
		 */
		private CompletableFuture<Object> handleAsync(Object promise) throws IllegalAccessException, InvocationTargetException {
			final CompletableFuture<Object> resultAsFuture = new CompletableFuture<>();
			register.invoke(promise, (Runnable) () -> {
				try {
					Throwable t = (Throwable) getFailure.invoke(promise);
					if(t != null) {
						resultAsFuture.completeExceptionally(t);
					} else {
						resultAsFuture.complete(getValue.invoke(promise));
					}
				} catch (Exception e) {
					resultAsFuture.completeExceptionally(e);
				}
			});
			return resultAsFuture;
		}
	}
}
