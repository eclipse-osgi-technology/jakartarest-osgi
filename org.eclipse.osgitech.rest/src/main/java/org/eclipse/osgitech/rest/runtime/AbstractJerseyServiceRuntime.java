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
package org.eclipse.osgitech.rest.runtime;

import static org.osgi.framework.Constants.SERVICE_CHANGECOUNT;
import static org.osgi.namespace.implementation.ImplementationNamespace.IMPLEMENTATION_NAMESPACE;
import static org.osgi.namespace.service.ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE;
import static org.osgi.namespace.service.ServiceNamespace.SERVICE_NAMESPACE;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_NAME;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_IMPLEMENTATION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_WHITEBOARD_SPECIFICATION_VERSION;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.annotations.RequireJerseyExtras;
import org.eclipse.osgitech.rest.annotations.RequireRuntimeAdapter;
import org.eclipse.osgitech.rest.binder.PrototypeServiceBinder;
import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.factories.InjectableFactory;
import org.eclipse.osgitech.rest.factories.JerseyResourceInstanceFactory;
import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.eclipse.osgitech.rest.provider.JerseyConstants;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider;
import org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplication;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.osgi.annotation.bundle.Capability;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseDTO;
import org.osgi.service.jakartars.runtime.dto.ExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.FailedExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceMethodInfoDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.Providers;
import jakarta.ws.rs.sse.Sse;

/**
 * Implementation of the {@link JakartarsServiceRuntime} for a Jersey implementation
 * @author Mark Hoffmann
 * @since 12.07.2017
 */

// Require the Jersey server runtime
@RequireJerseyExtras
// Require an adapter to run the whiteboard
@RequireRuntimeAdapter
// Advertise the implementation capability for the whiteboard as per the specification
@Capability(
		namespace = IMPLEMENTATION_NAMESPACE, 
		version = JAKARTA_RS_WHITEBOARD_SPECIFICATION_VERSION, 
		name = JAKARTA_RS_WHITEBOARD_IMPLEMENTATION, 
		uses = {JakartarsWhiteboardConstants.class, WebApplicationException.class, 
				Client.class, ResourceInfo.class, Application.class, Providers.class, 
				Sse.class},
		attribute = { "provider=jersey", "jersey.version=3.0" }
)
@Capability(
		namespace = SERVICE_NAMESPACE,
		uses = {JakartarsServiceRuntime.class, ResourceDTO.class},
		attribute = CAPABILITY_OBJECTCLASS_ATTRIBUTE + "=org.osgi.service.jakartars.runtime.JakartarsServiceRuntime"
)
public abstract class AbstractJerseyServiceRuntime implements JakartarsServiceRuntime, JakartarsWhiteboardProvider {

	private volatile RuntimeDTO runtimeDTO = new RuntimeDTO();
	private volatile String name;
	protected ComponentContext context;
	// hold all resource references of the default application 
	protected final Map<String, JakartarsApplicationProvider> applicationContainerMap = new ConcurrentHashMap<>();
	
//	hold the failed apps, resources and extensions for this whiteboard
	protected final List<FailedApplicationDTO> failedApplications = new LinkedList<>();
	protected final List<FailedResourceDTO> failedResources = new LinkedList<>();
	protected final List<FailedExtensionDTO> failedExtensions = new LinkedList<>();

	private Logger logger = Logger.getLogger("Jakartars.serviceRuntime");
	private ServiceRegistration<JakartarsServiceRuntime> regJakartarsServiceRuntime;
	private AtomicLong changeCount = new AtomicLong();

	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.runtime.JakartarsServiceRuntime#getRuntimeDTO()
	 */
	@Override
	public RuntimeDTO getRuntimeDTO() {
		synchronized (runtimeDTO) {
			return runtimeDTO;
		}
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#initialize(org.osgi.service.component.ComponentContext)
	 */
	@Override
	public void initialize(ComponentContext context) throws ConfigurationException {
		this.context = context;
		updateProperties(context);
		doInitialize(context);
		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#startup()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void startup() {
		doStartup();
		Dictionary<String, Object> properties = getRuntimePropertiesWithNewChangeCount();
		String[] service = new String[] {JakartarsServiceRuntime.class.getName(), JakartarsWhiteboardProvider.class.getName()};
		try {
			Bundle _bundle = FrameworkUtil.getBundle(AbstractJerseyServiceRuntime.class);
			regJakartarsServiceRuntime = (ServiceRegistration<JakartarsServiceRuntime>) _bundle.getBundleContext()
					.registerService(service, this, properties);
			updateRuntimeDtoAndChangeCount();

		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error starting JakartarsRuntimeService ", e);
			if (regJakartarsServiceRuntime != null) {
				regJakartarsServiceRuntime.unregister();
			}
		} 
	}

	/**
	 * Merges all available properties and adds a fitting changecount
	 * @return the properties that can be assigned to the changecount
	 */
	private Dictionary<String, Object> getRuntimePropertiesWithNewChangeCount() {
		Dictionary<String, Object> properties = new Hashtable<>();
		getProperties().entrySet().forEach(e -> properties.put(e.getKey(), e.getValue()));
		properties.put(JAKARTA_RS_SERVICE_ENDPOINT, getURLs(context));
		properties.put(JAKARTA_RS_NAME, name);
		properties.put(SERVICE_CHANGECOUNT, changeCount.incrementAndGet());
		return properties;
	}
	
	/**
	 * Updates the properties and the changecount of the registered Runtime
	 */
	private void updateRuntimeDtoAndChangeCount() {
					updateRuntimeDTO();
			//151.2.1 The JAX-RS Service Runtime Service 
			/*
			 * Whenever the DTOs available from the JAX-RS Service Runtime service change,
			 * the value of this property will increase.
			 * 
			 * This allows interested parties to be notified of changes to the DTOs by
			 * observing Service Events of type MODIFIED for the JakartarsServiceRuntime
			 * service. See org.osgi.framework.Constants.SERVICE_CHANGECOUNT in
			 */
			updateChangeCount();
		
	}


	private void updateChangeCount() {
		Dictionary<String, Object> properties = getRuntimePropertiesWithNewChangeCount();
		if(regJakartarsServiceRuntime!=null) {
			regJakartarsServiceRuntime.setProperties(properties);
		}

	}

	/**
	 * Handles the actual implementation specific Startup
	 */
	protected abstract void doStartup();
	
	/**
	 * Handles the distinct intilization 
	 * @param context the {@link ComponentContext} to use
	 */
	protected abstract void doInitialize(ComponentContext context) ;

	/* (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#modified(org.osgi.service.component.ComponentContext)
	 */
	@Override
	public void modified(ComponentContext context) throws ConfigurationException {
		doModified(context);
//		applicationContainerMap.clear();
		updateRuntimeDtoAndChangeCount();
	}
	
	protected abstract void doModified(ComponentContext context) throws ConfigurationException ;

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#teardown()
	 */
	public void teardown() {
		if (regJakartarsServiceRuntime != null) {
			try {
				regJakartarsServiceRuntime.unregister();
			} catch (IllegalStateException ise) {
				logger.log(Level.SEVERE, "JakartarsRuntime was already unregistered", ise);
			} catch (Exception ise) {
				logger.log(Level.SEVERE, "Error unregsitering JakartarsRuntime", ise);
			}
		}
		doTeardown();
	}

	/**
	 * Handles the distinct teardown event
	 */
	protected abstract void doTeardown();

	private synchronized void updateRuntimeDTO() {
			synchronized (runtimeDTO) {
			List<ApplicationDTO> appDTOList = new LinkedList<>();
			
			applicationContainerMap.forEach((name, ap) -> {
				BaseApplicationDTO appDTO = ap.getApplicationDTO();
				if (appDTO instanceof ApplicationDTO) {
					ApplicationDTO curDTO = (ApplicationDTO) appDTO;
					if (curDTO.name.equals(".default") || curDTO.base.equals("/")) {
						runtimeDTO.defaultApplication = curDTO;
					} else {
						appDTOList.add(curDTO);
					}
				} 	
			});
			
			if (regJakartarsServiceRuntime != null) {
				ServiceReference<?> serviceRef = regJakartarsServiceRuntime.getReference();
				if (serviceRef != null) {
					ServiceReferenceDTO srDTO = DTOConverter.toServiceReferenceDTO(serviceRef);
					runtimeDTO.serviceDTO = srDTO;
					// the defaults application service id is the same, like this, because it comes
					// from here
					// runtimeDTO.defaultApplication.serviceId = srDTO.id;
				}
			}
			runtimeDTO.applicationDTOs = appDTOList.toArray(new ApplicationDTO[appDTOList.size()]);		
			
//			We need to add the ResourceDTO which uses NameBinding with the corresponding Extension, for all app plus the default one		
			setExtResourceForNameBinding(runtimeDTO.applicationDTOs);
			setExtResourceForNameBinding(new ApplicationDTO[] {runtimeDTO.defaultApplication});

//			add the failed apps, resources and extensions DTOs	
			runtimeDTO.failedApplicationDTOs = failedApplications.toArray(new FailedApplicationDTO[failedApplications.size()]);
			runtimeDTO.failedExtensionDTOs = failedExtensions.toArray(new FailedExtensionDTO[failedExtensions.size()]); 
			runtimeDTO.failedResourceDTOs = failedResources.toArray(new FailedResourceDTO[failedResources.size()]); 
		}

	}
	


	private void setExtResourceForNameBinding(ApplicationDTO[] apps) {
		for(ApplicationDTO aDTO : apps) {
			if(aDTO==null) {
				continue;
			}
			Map<String, Set<ResourceDTO>> extResNameBind = new HashMap<>();
			for(ResourceDTO rDTO : aDTO.resourceDTOs) {
				for(ResourceMethodInfoDTO mDTO : rDTO.resourceMethods) {
					if(mDTO.nameBindings != null && mDTO.nameBindings.length > 0) {
						for(String n : mDTO.nameBindings) {
							for(ExtensionDTO extDTO : aDTO.extensionDTOs) {
								if(extDTO.nameBindings != null && extDTO.nameBindings.length > 0) {
									for(String en : extDTO.nameBindings) {
										if(n.equals(en)) {
											if(!extResNameBind.containsKey(extDTO.name)) {
												extResNameBind.put(extDTO.name, new HashSet<ResourceDTO>());
											}
											extResNameBind.get(extDTO.name).add(rDTO);
										}
									}
								}
							}
						}
					}
				}
			}
			for(ExtensionDTO extDTO : aDTO.extensionDTOs) {
				if(extResNameBind.containsKey(extDTO.name)) {
					extDTO.filteredByName = extResNameBind.get(extDTO.name).toArray(new ResourceDTO[0]);
				}
			}
		}
	}



	@Override
	public void registerApplication(JakartarsApplicationProvider applicationProvider) {
		if (applicationProvider == null) {
			logger.log(Level.WARNING, "Cannot register an null application provider");
			return;
		}
		if (applicationContainerMap.containsKey(applicationProvider.getId())) {
			logger.log(Level.SEVERE, "There is already an application registered with name: " + applicationProvider.getId());
			throw new IllegalStateException("There is already an application registered with name: " + applicationProvider.getId());
		}
		String applicationPath = applicationProvider.getPath();
		doRegisterServletContext(applicationProvider, applicationPath);
		applicationContainerMap.put(applicationProvider.getId(), applicationProvider);
		updateRuntimeDtoAndChangeCount();
	}

	/**
	 * Handles the distinct operation of adding the given application servlet for the given path 
	 * @param container the container servlet to add
	 * @param path to path to add it for
	 */
	protected abstract void doRegisterServletContext(JakartarsApplicationProvider provider, String path, ResourceConfig config);
	
	protected abstract void doRegisterServletContext(JakartarsApplicationProvider provider, String path);

	@Override
	public void unregisterApplication(JakartarsApplicationProvider applicationProvider) {
		if (applicationProvider == null) {
			logger.log(Level.WARNING, "Cannot unregister an null application provider");
			return;
		}
		JakartarsApplicationProvider provider = null;
		synchronized (applicationContainerMap) {
			provider = applicationContainerMap.remove(applicationProvider.getId()); //we are keeping track of the failed app elsewhere
		}
		if (provider == null) {
			logger.log(Level.WARNING, "There is no application registered with the name: " + applicationProvider.getName());
			return;
		}
		doUnregisterApplication(provider);
		updateRuntimeDtoAndChangeCount();
	}

	/**
	 * Handles the destinct unregistration of the servlets
	 * @param applicationProvider {@link JakartarsApplicationProvider} to unregister
	 */
	protected abstract void doUnregisterApplication(JakartarsApplicationProvider applicationProvider);

	@Override
	public void reloadApplication(JakartarsApplicationProvider applicationProvider) {
		if (applicationProvider == null) {
			logger.log(Level.WARNING, "No application provider was given to be reloaded");
		}
		logger.log(Level.INFO, "Reload an application provider " + applicationProvider.getName());
		JakartarsApplicationProvider provider = applicationContainerMap.get(applicationProvider.getId());
		if (provider == null) {
			logger.log(Level.INFO, "No application provider was registered nothing to reload, registering instead for " + applicationProvider.getId());
			registerApplication(applicationProvider);
		} else {
			applicationContainerMap.put(applicationProvider.getId(), applicationProvider);
			List<ServletContainer> servletContainers = provider.getServletContainers();
			if(servletContainers.isEmpty()) {
				logger.log(Level.INFO, "-- No servlet container is available to reload " + applicationProvider.getName());
			} else {
				logger.log(Level.FINE, "Reload servlet container for application " + applicationProvider.getName());
				
				List<ServletContainer> copyList = new ArrayList<>(servletContainers);
				
				copyList.forEach(servletContainer -> {
					try{
						ResourceConfigWrapper config = createResourceConfig(provider);
						
						((WhiteboardServletContainer) servletContainer).reloadWrapper(config);
					} catch(Exception e) {
						//We cant't check if the surrounding container is started, so we have to do it this way
						logger.log(Level.WARNING, "Jetty servlet context handler is not started yet", e);
					}
				});
			}
			//App Properties could be changed
			updateRuntimeDtoAndChangeCount();
		}
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#isRegistered(org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider)
	 */
	@Override
	public boolean isRegistered(JakartarsApplicationProvider provider) {
		if (provider == null) {
			return false;
		}
		return applicationContainerMap.containsKey(provider.getId());
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#getName()
	 */
	@Override
	public String getName() {
		return name;
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#getProperties()
	 */
	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> properties = new HashMap<>();
		
		Enumeration<String> keys = context.getProperties().keys();
		if (regJakartarsServiceRuntime != null) {
			String[] runtimeKeys = regJakartarsServiceRuntime.getReference().getPropertyKeys();
			for (String k : runtimeKeys) {
				properties.put(k, regJakartarsServiceRuntime.getReference().getProperty(k));
			}
		}
		while(keys.hasMoreElements()) {
			String key = keys.nextElement();
			Object value = context.getProperties().get(key);
			properties.put(key, value);
		}
		return properties;
	}

	/**
	 * Creates a new {@link ResourceConfig} for a given application. this method takes care of registering
	 * Jersey factories for prototype scoped resource services and singletons separately
	 * @param applicationProvider the Jakartars application application provider
	 */
	protected ResourceConfigWrapper createResourceConfig(JakartarsApplicationProvider applicationProvider) {
		if (applicationProvider == null) {
			logger.log(Level.WARNING, "Cannot create a resource configuration for null application provider");
			return null;
		}
		Application application = applicationProvider.getJakartarsApplication();
		if(application instanceof JerseyApplication) {
			((JerseyApplication) application).resetForReload();
		}
		ResourceConfigWrapper wrapper = new ResourceConfigWrapper();
		ResourceConfig config = ResourceConfig.forApplication(application);
		final Map<String, Object> properties = new HashMap<String, Object>(config.getProperties());
		properties.put(ServerProperties.RESOURCE_VALIDATION_IGNORE_ERRORS, Boolean.TRUE);
		config.setProperties(properties);
        wrapper.config = config;
		
		PrototypeServiceBinder resBinder = new PrototypeServiceBinder();
		AtomicBoolean resRegistered = new AtomicBoolean(false);
		
		applicationProvider.getContentProviers().stream().sorted().forEach(provider -> {					
			logger.info("Register prototype provider for classes " + provider.getObjectClass() + " in the application " + applicationProvider.getId());
			logger.info("Register prototype provider for name " + provider.getName() + " id " + provider.getId() + " rank " + provider.getServiceRank());
			if (context == null) {
				throw new IllegalStateException("Cannot create prototype factories without component context");
			}
			InjectableFactory<?> factory = null;
			if(provider instanceof JakartarsResourceProvider) {
				resRegistered.set(true);
				factory = new JerseyResourceInstanceFactory<>(provider);
				resBinder.register(provider.getObjectClass(), factory);
			}
			if(factory != null) {
				wrapper.factories.add(factory);
			}
		});
		if (resRegistered.get()) {
			config.register(resBinder);
		}
		return wrapper;
	}

	/**
	 * Updates the fields that are provided by service properties.
	 * @param ctx the component context
	 * @throws ConfigurationException thrown when no context is available or the expected property was not provided 
	 */
	protected void updateProperties(ComponentContext ctx) throws ConfigurationException {
		if (ctx == null) {
			throw new ConfigurationException(JAKARTA_RS_SERVICE_ENDPOINT, "No component context is availble to get properties from");
		}
		name = JerseyHelper.getPropertyWithDefault(ctx, JAKARTA_RS_NAME, null);
		if (name == null) {
			name = JerseyHelper.getPropertyWithDefault(ctx, JerseyConstants.JERSEY_WHITEBOARD_NAME, JerseyConstants.JERSEY_WHITEBOARD_NAME);
			if (name == null) {
				throw new ConfigurationException(JAKARTA_RS_NAME, "No name was defined for the whiteboard");
			}
		}
		doUpdateProperties(ctx);
		updateRuntimeDtoAndChangeCount();
	}

	/**
	 * Handles the distinct update properties event
	 * @param ctx the {@link ComponentContext} to use
	 * @throws ConfigurationException 
	 */
	protected abstract void doUpdateProperties(ComponentContext ctx) throws ConfigurationException;
	
	
	public synchronized void updateFailedContents(Map<String, JakartarsApplicationProvider> failedAppProviders, 
			Map<String, JakartarsResourceProvider> failedResourcesProviders, 
			Map<String, JakartarsExtensionProvider> failedExtensionsProviders) {

		failedApplications.clear();
		failedResources.clear();
		failedExtensions.clear();
		
		failedAppProviders.values().stream().forEach(p-> {
			BaseApplicationDTO dto = p.getApplicationDTO();
			if(dto instanceof FailedApplicationDTO) {
				failedApplications.add((FailedApplicationDTO) dto);
			}
			else {
				throw new IllegalStateException("Failed Application Provider " + p.getName() + " does not have a FailedApplicationDTO");
			}
		});

		failedResourcesProviders.values().stream().forEach(p-> {
			BaseDTO dto = p.getResourceDTO();
			if(dto instanceof FailedResourceDTO) {
				failedResources.add((FailedResourceDTO) dto);
			}
			else {
				throw new IllegalStateException("Failed Resource Provider " + p.getName() + " does not have a FailedResourceDTO");
			}
		});

		failedExtensionsProviders.values().stream().forEach(p-> {
			BaseDTO dto = p.getExtensionDTO();
			if(dto instanceof FailedExtensionDTO) {
				failedExtensions.add((FailedExtensionDTO) dto);
			}
			else {
				throw new IllegalStateException("Failed Extension Provider " + p.getName() + " does not have a FailedExtensionDTO");
			}
		});

		updateRuntimeDtoAndChangeCount();
	}
}
