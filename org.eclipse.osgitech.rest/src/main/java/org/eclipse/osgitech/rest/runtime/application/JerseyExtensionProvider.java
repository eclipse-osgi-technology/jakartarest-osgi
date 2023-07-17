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
package org.eclipse.osgitech.rest.runtime.application;

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.proxy.ExtensionProxyFactory;
import org.glassfish.jersey.InjectionManagerProvider;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.jakartars.runtime.dto.BaseExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.WriterInterceptor;

/**
 * A wrapper class for a Jakartars extensions 
 * @author Mark Hoffmann
 * @param <T>
 * @since 09.10.2017
 */
public class JerseyExtensionProvider extends JerseyApplicationContentProvider {

	private static final Logger logger = Logger.getLogger("jersey.extensionProvider");
	
	private static final List<String> POSSIBLE_INTERFACES = Arrays.asList(new String[] {
		ContainerRequestFilter.class.getName(),
		ContainerResponseFilter.class.getName(),
		ReaderInterceptor.class.getName(),
		WriterInterceptor.class.getName(),
		MessageBodyReader.class.getName(),
		MessageBodyWriter.class.getName(),
		ContextResolver.class.getName(),
		ExceptionMapper.class.getName(),
		ParamConverterProvider.class.getName(),
		Feature.class.getName(),
		DynamicFeature.class.getName()
	});
	
	private Class<?>[] contracts = null;
	
	private ClassLoader proxyClassLoader = null;
	
	public JerseyExtensionProvider(ServiceObjects<Object> serviceObjects, Map<String, Object> properties) {
		super(serviceObjects, properties);
		checkExtensionProperty(properties);
		extractContracts(properties);
		
	}
	
	/**
	 * If the ExtensionProvider does not advertise the property osgi.jakartars.extension as true then it is not a 
	 * valid extension
	 * 
	 * @param properties
	 */
	private void checkExtensionProperty(Map<String, Object> properties) {
		if(!properties.containsKey(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION) || 
				properties.get(JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION).equals(false)) {
			
			updateStatus(DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE);
		}
		
	}

	private void extractContracts(Map<String, Object> properties) {
		String[] objectClasses = (String[]) properties.get(Constants.OBJECTCLASS);
		List<Class<?>> possibleContracts = new ArrayList<>(objectClasses.length);
		for (String objectClass : objectClasses) {
			if(POSSIBLE_INTERFACES.contains(objectClass)) {
				try {
					possibleContracts.add(Class.forName(objectClass));
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			} 
		}
		if(!possibleContracts.isEmpty()) {
			contracts = possibleContracts.toArray(new Class[0]);
		}
		else {
			updateStatus(DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE); //if possibleContracts is empty the extension should record a failure DTO
		}
	}

	/** 
	 * Get the DTO representing this extension
	 */
	public BaseExtensionDTO getExtensionDTO() {
		int status = getProviderStatus();
		if (status == NO_FAILURE) {
			return DTOConverter.toExtensionDTO(this);
		} else if (status == INVALID) {
			return DTOConverter.toFailedExtensionDTO(this, DTOConstants.FAILURE_REASON_NOT_AN_EXTENSION_TYPE);
		} else {
			return DTOConverter.toFailedExtensionDTO(this, status);
		}
	}
	
	/** 
	 * Get the extension contracts advertised by this provider
	 */
	public Class<?>[] getContracts() {
		return contracts;
	}
	
	@Override
	public JerseyExtensionProvider cleanCopy() {
		return new JerseyExtensionProvider(getProviderObject(), getProviderProperties());
	}

	/**
	 * Returns the {@link JakartarsWhiteboardConstants} for this resource type 
	 * @return the {@link JakartarsWhiteboardConstants} for this resource type
	 */
	protected String getJakartarsResourceConstant() {
		return JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION;
	}

	/**
	 * Get an extension lifecycle manager suitable for use in the {@link JerseyApplication}
	 * @param context
	 * @return
	 */
	public JerseyExtension getExtension(FeatureContext context) {
		Object service = Optional.ofNullable(getProviderObject())
				.map(ServiceObjects::getService)
				.orElse(null);
		if(service == null) {
			logger.severe("Unable to get the service object for service " + getId() + " with name " + getName() + 
					" and contracts" + Arrays.stream(contracts).map(Class::getName).collect(Collectors.joining(", ")));
			return null;
		}
		InjectionManagerProvider.getInjectionManager(context).inject(service);
		return new JerseyExtension(service);
	}
	
	/**
	 * The JerseyExtension encapsulates an instance of the extension and
	 * wraps it in a generated class for use in a running application.
	 * 
	 * This instance can be released by calling {@link #dispose()}
	 */
	public class JerseyExtension {
		
		private Object delegate;
		
		/**
		 * Creates a new instance.
		 * @param delegate
		 */
		public JerseyExtension(Object delegate) {
			this.delegate = delegate;
		}

		/**
		 * Return the map with contract priorities. If the Priority annotation is not
		 * used, an empty map will be returned. 
		 */
		public Map<Class<?>, Integer> getContractPriorities() {
			Integer priority = Arrays.stream(delegate.getClass().getAnnotations())
				.filter(a -> a.annotationType().getName().equals("jakarta.annotation.Priority"))
				.findFirst()
				.map(a -> {
					try {
						return (Integer) a.getClass().getMethod("value").invoke(a);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}).orElse(null);
			if (priority == null) {
				return Collections.emptyMap();
			}
			return Arrays.stream(contracts).collect(toMap(Function.identity(), c -> priority));
		}
		
		/**
		 * Get the extension object
		 */
		public Object getExtensionObject() {
			if(proxyClassLoader == null) {
				proxyClassLoader = new ClassLoader(
						getProviderObject().getServiceReference().getBundle()
						.adapt(BundleWiring.class).getClassLoader()) {

							/* 
							 * (non-Javadoc)
							 * @see java.lang.ClassLoader#findClass(java.lang.String)
							 */
							@Override
							protected Class<?> findClass(String name) throws ClassNotFoundException {
								byte[] b = ExtensionProxyFactory.generateClass(name, getDelegate(), Arrays.asList(contracts));
								return defineClass(name, b, 0, b.length, delegate.getClass().getProtectionDomain());
							}
					
					
				};
			}
			String simpleName = ExtensionProxyFactory.getSimpleName(getServiceRank(), getServiceId());
			
			try {
				Class<?> clz = proxyClassLoader.loadClass("org.eclipse.osgitech.rest.proxy." + simpleName);
				return clz.getConstructor(Supplier.class).newInstance((Supplier<?>)this::getDelegate);
				
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private Object getDelegate() {
			Object toReturn;
			synchronized (this) {
				toReturn = delegate;
			}
			if(toReturn == null) {
				throw new IllegalStateException("The target extension " + getName() + " has been disposed");
			}
			return toReturn;
		}

		/**
		 * Release the provider object
		 */
		public void dispose() {
			Object toRelease;
			synchronized (this) {
				toRelease = delegate;
				delegate = null;
			}
			if(toRelease != null) {
				getProviderObject().ungetService(toRelease);
			}
		}
	}
}
