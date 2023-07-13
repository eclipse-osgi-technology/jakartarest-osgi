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

import static java.lang.Integer.MIN_VALUE;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Stream.concat;
import static org.osgi.framework.Constants.SERVICE_CHANGECOUNT;
import static org.osgi.framework.Constants.SERVICE_RANKING;
import static org.osgi.service.jakartars.runtime.JakartarsServiceRuntimeConstants.JAKARTA_RS_SERVICE_ENDPOINT;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_DEFAULT_APPLICATION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_NAME;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.osgitech.rest.annotations.RequireJerseyExtras;
import org.eclipse.osgitech.rest.annotations.RequireRuntimeAdapter;
import org.eclipse.osgitech.rest.binder.PrototypeServiceBinder;
import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.factories.InjectableFactory;
import org.eclipse.osgitech.rest.factories.JerseyResourceInstanceFactory;
import org.eclipse.osgitech.rest.helper.DispatcherHelper;
import org.eclipse.osgitech.rest.runtime.application.AbstractJakartarsProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplication;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationContentProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyExtensionProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyResourceProvider;
import org.glassfish.jersey.InjectionManagerProvider;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.spi.AbstractContainerLifecycleListener;
import org.glassfish.jersey.server.spi.Container;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseDTO;
import org.osgi.service.jakartars.runtime.dto.BaseExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.ExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.FailedExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceMethodInfoDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;
import org.osgi.util.tracker.ServiceTracker;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

/**
 * Implementation of the {@link JakartarsServiceRuntime} for a Jersey implementation
 * @author Mark Hoffmann
 * @since 12.07.2017
 */

// Require the Jersey server runtime
@RequireJerseyExtras
// Require an adapter to run the whiteboard
@RequireRuntimeAdapter
public class JerseyServiceRuntime<C extends Container> {

	private static final String RESOURCE_FILTER = "(" + JAKARTA_RS_RESOURCE + "=true)";
	private static final String EXTENSION_FILTER = "(" + JAKARTA_RS_EXTENSION + "=true)";
	private static final String APPLICATION_FILTER = "(&(objectClass=" + Application.class.getName() + ")(" + JAKARTA_RS_APPLICATION_BASE + "=*))";
	
	/** Used to synchronize internal updates */
	private final Object lock = new Object();
	
	/** Whether the whiteboard is running, protected by {@link #lock}*/
	private Boolean active = null; 
	
	/** The time of the last update, protected by {@link #lock}*/
	private Instant lastUpdate;
	
	/** The current runtime dto, protected by {@link #lock} */
	private RuntimeDTO runtimeDTO = new RuntimeDTO();
	/** 
	 * The current update count, counts the updates (i.e. whiteboard
	 * service changes). Protected by {@link #lock} 
	 */
	private long updateCount;
	/** 
	 * The current update count, marks the update count currently
	 * reflected in the runtime DTO. Protected by {@link #lock} 
	 */
	private long changeCount;
	
	/** 
	 * The currently set runtime properties, not necessarily reflected in 
	 * the service registration until the update has finished.
	 * Protected by {@link #lock} 
	 */
	private Map<String, Object> runtimeProperties;
	
	/** The update worker thread **/
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private final BundleContext context;
	
	private final BiFunction<String, ResourceConfig, C> containerFactory;
	private final BiConsumer<String, C> containerDestroyer;
	private final ServiceTracker<Object, ServiceReference<?>> resourceTracker;
	private final ServiceTracker<Object, ServiceReference<?>> extensionTracker;
	private final ServiceTracker<Application, Application> applicationTracker;
	
	/**
	 * Empty Whiteboard Application services. Protected by {@link #lock}
	 */
	private final Map<String, JerseyApplicationProvider> applicationContainerMap = new HashMap<>();
	
	/**
	 * Empty Whiteboard Extension services. Protected by {@link #lock}
	 */
	private final Map<String, JerseyExtensionProvider> extensionMap = new HashMap<>();
	
	/**
	 * Empty Whiteboard Resource services. Protected by {@link #lock}
	 */
	private final Map<String, JerseyResourceProvider> resourceMap = new HashMap<>();
	
	// The implicit default application, may be shadowed by a registered service
	private final JerseyApplicationProvider defaultProvider = new JerseyApplicationProvider(new Application(), 
			Map.of(JAKARTA_RS_NAME, JAKARTA_RS_DEFAULT_APPLICATION,
					JAKARTA_RS_APPLICATION_BASE, "/",
					SERVICE_RANKING, MIN_VALUE));;

	private final Logger logger = Logger.getLogger("Jakartars.serviceRuntime");
	
	/**
	 * This reference must only be set or used by the executor thread
	 */
	private ServiceRegistration<JakartarsServiceRuntime> regJakartarsServiceRuntime;
	
	/**
	 * This reference must only be used by the executor thread
	 */
	private final Map<String, C> containersByPath = new HashMap<>();

	public JerseyServiceRuntime(BundleContext context, BiFunction<String, ResourceConfig, C> containerFactory,
			BiConsumer<String, C> containerDestroyer) {
		super();
		this.context = context;
		this.containerFactory = containerFactory;
		this.containerDestroyer = containerDestroyer;
		
		try {
			resourceTracker = new ServiceTracker<>(context, context.createFilter(RESOURCE_FILTER), null) {
	
				@Override
				public ServiceReference<?> addingService(ServiceReference<Object> reference) {
					JerseyResourceProvider provider = new JerseyResourceProvider(
							context.getServiceObjects(reference), getServiceProps(reference));
					updateMap(resourceMap, provider);
					return reference;
				}
	
				@Override
				public void modifiedService(ServiceReference<Object> reference, ServiceReference<?> service) {
					JerseyResourceProvider provider = new JerseyResourceProvider(
							context.getServiceObjects(reference), getServiceProps(reference));
					updateMap(resourceMap, provider);
				}
	
				@Override
				public void removedService(ServiceReference<Object> reference, ServiceReference<?> service) {
					JerseyResourceProvider provider = new JerseyResourceProvider(
							null, getServiceProps(reference));
					clearMap(resourceMap, provider);
				}
				
			};
	
			extensionTracker = new ServiceTracker<>(context, context.createFilter(EXTENSION_FILTER), null) {
				
				@Override
				public ServiceReference<?> addingService(ServiceReference<Object> reference) {
					JerseyExtensionProvider provider = new JerseyExtensionProvider(
							context.getServiceObjects(reference), getServiceProps(reference));
					updateMap(extensionMap, provider);
					return reference;
				}
				
				@Override
				public void modifiedService(ServiceReference<Object> reference, ServiceReference<?> service) {
					JerseyExtensionProvider provider = new JerseyExtensionProvider(
							context.getServiceObjects(reference), getServiceProps(reference));
					updateMap(extensionMap, provider);
				}
				
				@Override
				public void removedService(ServiceReference<Object> reference, ServiceReference<?> service) {
					JerseyExtensionProvider provider = new JerseyExtensionProvider(
							context.getServiceObjects(reference), getServiceProps(reference));
					clearMap(extensionMap, provider);
				}
				
			};
			
			applicationTracker = new ServiceTracker<>(context, context.createFilter(APPLICATION_FILTER), null) {
	
				@Override
				public Application addingService(ServiceReference<Application> reference) {
					Application app = super.addingService(reference);
					JerseyApplicationProvider provider = new JerseyApplicationProvider(
							app, getServiceProps(reference));
					updateMap(applicationContainerMap, provider);
					return app;
				}
	
				@Override
				public void modifiedService(ServiceReference<Application> reference,
						Application service) {
					JerseyApplicationProvider provider = new JerseyApplicationProvider(
							service, getServiceProps(reference));
					updateMap(applicationContainerMap, provider);
				}
	
				@Override
				public void removedService(ServiceReference<Application> reference, Application service) {
					JerseyApplicationProvider provider = new JerseyApplicationProvider(
							null, getServiceProps(reference));
					clearMap(applicationContainerMap, provider);
					super.removedService(reference, service);
				}
				
			};
		} catch (InvalidSyntaxException ise) {
			throw new RuntimeException("An error occurred creating a filter from a static String", ise);
		}
	}

	private Map<String, Object> getServiceProps(ServiceReference<?> ref) {
		return Arrays.stream(ref.getPropertyKeys())
			.collect(toMap(identity(), ref::getProperty));
	}
	
	private <R, T extends AbstractJakartarsProvider<R>> void updateMap(Map<String, T> map, T provider) {
		synchronized (lock) {
			scheduleUpdate();
			map.put(provider.getId(), provider);
			updateCount++;
		}
	}

	private <R, T extends AbstractJakartarsProvider<R>> void clearMap(Map<String, T> map, T provider) {
		synchronized (lock) {
			scheduleUpdate();
			map.remove(provider.getId());
			updateCount++;
		}
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.osgi.service.jakartars.runtime.JakartarsServiceRuntime#getRuntimeDTO()
	 */
	public RuntimeDTO getRuntimeDTO() {
		RuntimeDTO dto;
		synchronized (lock) {
			dto = runtimeDTO;
		}
		return DTOConverter.deepCopy(dto);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#initialize(org.osgi.service.component.ComponentContext)
	 */
	public void start(Map<String, Object> runtimeProperties) {
		
		synchronized (lock) {
			active = Boolean.TRUE;
			lastUpdate = Instant.ofEpochMilli(0);
			// Delay the first update to catch the first services when we open the trackers
			scheduleUpdate();
			this.runtimeProperties = Map.copyOf(runtimeProperties);
			updateCount++;
		}
		applicationTracker.open();
		extensionTracker.open();
		resourceTracker.open();
		
	}
	
	public void update(Map<String, Object> runtimeProperties) {
		synchronized (lock) {
			scheduleUpdate();
			this.runtimeProperties = Map.copyOf(runtimeProperties);
			updateCount++;
		}
	}
	
	/**
	 * Call while holding {@link #lock}
	 */
	private void scheduleUpdate() {
		if(active == Boolean.TRUE && updateCount == changeCount) {
			// Wait for at least 50 millis between updates to avoid excessive churn
			long since = Duration.between(lastUpdate, Instant.now()).toMillis();
			long delay = since < 50 ? 50 - since : 0;
			executor.schedule(() -> doInternalUpdate(), delay, TimeUnit.MILLISECONDS);
		}
	}
	
	/**
	 * Only to be called by the executor thread
	 */
	private void doInternalUpdate() {
		
		long changeCount;
		Map<String, Object> runtimeProperties;
		List<JerseyApplicationProvider> applications;
		List<JerseyExtensionProvider> extensions;
		List<JerseyResourceProvider> resources;
		
		Instant update = Instant.now();
		synchronized (lock) {
			if(active != Boolean.TRUE)
				return;
			this.lastUpdate = update;
			this.changeCount = updateCount;
			changeCount = updateCount;
			runtimeProperties = regJakartarsServiceRuntime == null ? Map.copyOf(this.runtimeProperties) :
				FrameworkUtil.asMap(regJakartarsServiceRuntime.getReference().getProperties());
			// Always use clean copies to avoid polluting the source inputs
			applications = concat(Stream.of(defaultProvider), applicationContainerMap.values().stream())
					.map(jap -> jap.cleanCopy()).collect(toList());
			extensions = extensionMap.values().stream()
					.map(jep -> jep.cleanCopy()).collect(toList());
			resources = resourceMap.values().stream()
					.map(jrp -> jrp.cleanCopy()).collect(toList());
		}
		
		
		try {
			doDispatch(runtimeProperties, applications, extensions, resources);
			
			RuntimeDTO dto = getUpdatedRuntimeDTO(runtimeProperties, applications, extensions, resources);
			
			synchronized (lock) {
				runtimeDTO = dto;
			}

			Dictionary<String, Object> properties = getRuntimePropertiesWithNewChangeCount(runtimeProperties, changeCount);
			
			if(regJakartarsServiceRuntime == null) {
				regJakartarsServiceRuntime = context.registerService(JakartarsServiceRuntime.class, this::getRuntimeDTO, properties);
			} else {
				regJakartarsServiceRuntime.setProperties(properties);
			}
			// Update this now we have the correct reference
			ServiceReferenceDTO updatedDto = DTOConverter.toServiceReferenceDTO(regJakartarsServiceRuntime.getReference()); 
			synchronized (lock) {
				runtimeDTO.serviceDTO = updatedDto;
			}

		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error updating JerseyServiceRuntime", e);
			if (regJakartarsServiceRuntime != null) {
				regJakartarsServiceRuntime.unregister();
				regJakartarsServiceRuntime = null;
			}
		} 
	}
	
	/**
	 * Merges all available properties and adds a fitting changecount
	 * @return the properties that can be assigned to the changecount
	 */
	private Dictionary<String, Object> getRuntimePropertiesWithNewChangeCount(Map<String, Object> runtimeProps, long changeCount) {
		Dictionary<String, Object> properties = new Hashtable<>(runtimeProps);
		if(!runtimeProps.containsKey(JAKARTA_RS_SERVICE_ENDPOINT)) {
			properties.put(JAKARTA_RS_SERVICE_ENDPOINT, List.of("/"));
		}
		properties.put(SERVICE_CHANGECOUNT, changeCount);
		return properties;
	}

	private void doDispatch(Map<String, Object> properties, 
			List<JerseyApplicationProvider> applications, List<JerseyExtensionProvider> extensions, 
			List<JerseyResourceProvider> resources) {
		try {
		
			/*
			 * Determine all applications, resources and extension that fit to the whiteboard.
			 * We only work with those, because all these are possible candidates for the whiteboard
			 * We remove the values as we want the Lists from the caller to shrink to only the correct
			 * services for generating the RuntimeDTO
			 */
			applications.removeIf(a -> !a.canHandleWhiteboard(properties));
			extensions.removeIf(e -> !e.canHandleWhiteboard(properties));
			resources.removeIf(r -> !r.canHandleWhiteboard(properties));
			
			/* From this point on we work with candidate lists containing only valid options */
			
			/*
			 * Go over all applications and filter application with same path (shadowed) ordered by service rank (highest first)
			 * Check substitution of an application through the matched one 
			 * Non matching applications will be filtered out and marked as failing.
			 * #19 151.6.1
			 */
			List<JerseyApplicationProvider> applicationCandidates = checkPathProperty(applications);		
					
//				Check the osgi.jakartars.name property and filter out services with same name and lower rank
			List<AbstractJakartarsProvider<?>> candidates = checkNameProperty(applicationCandidates, extensions, resources);
			
			applicationCandidates = candidates.stream()
					.filter(JerseyApplicationProvider.class::isInstance)
					.map(JerseyApplicationProvider.class::cast)
					.collect(Collectors.toUnmodifiableList());
			
			List<JerseyResourceProvider> resourceCandidates = candidates.stream()
					.filter(JerseyResourceProvider.class::isInstance)
					.map(JerseyResourceProvider.class::cast)
					.collect(Collectors.toUnmodifiableList());
			
			List<JerseyExtensionProvider> extensionCandidates = candidates.stream()
					.filter(JerseyExtensionProvider.class::isInstance)
					.map(JerseyExtensionProvider.class::cast)
					.collect(Collectors.toUnmodifiableList());			
			
			
//				Assign extension to apps and report a failure DTO for those extensions which have not been assigned to any app
			assignContent(applicationCandidates, extensionCandidates);
			
			
//				check for osgi.jakartars.extension.select properties in apps and extensions
//				If such property exists we should check that the corresponding extensions are available,
//				otherwise the service should result in a failure DTO
			applicationCandidates = checkExtensionSelect(applicationCandidates, extensions, properties);	
			
			/*
			 * Determine all default applications. We are only interested in the highest ranked one, that
			 * will substitute the implicit default application. All other default applications are added 
			 * to the failed application list
			 * Section 151.6.1
			 * 
			 * Go over all applications and filter application with name '.default' ordered by service rank (highest first)
			 * Check substitution of defaultProvider through this application
			 * No matching applications should be stored in the failedApplication list. All failed application from
			 * this step should be filtered out of the application list
			 * #18 151.6.1
			 */
			Set<JerseyApplicationProvider> defaultApplications = DispatcherHelper.getDefaultApplications(applicationCandidates);
			
			defaultApplications
				.stream()
				.skip(1)// the default app
				.forEach(a-> {
					a.updateStatus(DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE);
				});
			
//				Filter out from the application list the default ones which have been added to the failed list
			applicationCandidates = applicationCandidates.stream().filter(not(AbstractJakartarsProvider::isFailed))
					.collect(Collectors.toUnmodifiableList());	
			
//				Assign resources to apps and report a failure DTO for those resources which have not been added to any app
			assignContent(applicationCandidates, resourceCandidates);
			
//				check for osgi.jakartars.extension.select properties in apps and resources
//				If such property exists we should check that the corresponding extensions are available,
//				otherwise the service should result in a failure DTO
			checkExtensionSelectForResources(applicationCandidates, resources, properties);
			
			// We now have our full set of applications
			
			for(JerseyApplicationProvider jap : applicationCandidates) {
				C c = containersByPath.get(jap.getPath());
				if(c == null) {
					c = containerFactory.apply(jap.getPath(), createResourceConfig(jap));
					containersByPath.put(jap.getPath(), c);
					continue;
				}
				
				Application application = c.getConfiguration().getApplication();
				if(jap.isChanged(application)) {
					c.reload(createResourceConfig(jap));
				}
			}
			Set<String> paths = applicationCandidates.stream()
					.map(JerseyApplicationProvider::getPath)
					.collect(toSet());
			
			Iterator<Entry<String, C>> it = containersByPath.entrySet().iterator();
			while(it.hasNext()) {
				Entry<String, C> e = it.next();
				if(!paths.contains(e.getKey())) {
					C container = e.getValue();
					if(container != null) {
						containerDestroyer.accept(e.getKey(), container);
					}
					it.remove();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 151.6.1: The base URI for each application within the whiteboard must be unique. 
	 * If two or more applications targeting the same whiteboard are registered with the same base URI 
	 * then only the highest ranked service will be made available. 
	 * All other application services with that URI will have a failure DTO created for them. 
	 * 
	 * @param applicationCandidates the candidates apps. They are already ordered by rank because they passed before through
	 * the checkNameProperty method
	 * 
	 * @return the surviving apps after this check
	 */
	private List<JerseyApplicationProvider> checkPathProperty(List<JerseyApplicationProvider> applicationCandidates) {
		
		logger.fine("App Candidates size BEFORE ordering " + applicationCandidates.size());
		
		applicationCandidates = applicationCandidates.stream()
				.filter(not(AbstractJakartarsProvider::isFailed))
				.sorted()
				.collect(Collectors.toUnmodifiableList());
		
		logger.fine("App Candidates size AFTER ordering " + applicationCandidates.size());

		
		for(int i = 0; i < applicationCandidates.size(); i++) {
			JerseyApplicationProvider a1 = applicationCandidates.get(i);
			String path = a1.getPath();
			for(int j = i+1; j < applicationCandidates.size(); j++) {
				JerseyApplicationProvider a2 = applicationCandidates.get(j);
				if(path.equals(a2.getPath())) {
					logger.fine("Failing DTO status for App " + a2.getId());						
					a2.updateStatus(DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE);
				}
			}
		}
		return applicationCandidates.stream().filter(not(AbstractJakartarsProvider::isFailed)).collect(toList());
	}
	
	/**
	 * Check the osgi.jakartars.name property of all services associated with a whiteboard.
	 * If two or more services have the same name property, only the highest ranked one should be
	 * kept. The others should result in a failure DTO 
	 * 
	 * @param applicationCandidates
	 * @param resourceCandidates
	 * @param extensionCandidates
	 * @return a set of AbstractJakartarsProvider containing the surviving services
	 */
	private List<AbstractJakartarsProvider<?>> checkNameProperty(List<JerseyApplicationProvider> applicationCandidates,
			List<JerseyExtensionProvider> extensionCandidates, List<JerseyResourceProvider> resourceCandidates) {			
		
		@SuppressWarnings("unchecked")
		List<AbstractJakartarsProvider<?>> allCandidates = Stream.of(applicationCandidates.stream(), extensionCandidates.stream(), resourceCandidates.stream())
				.flatMap(s -> (Stream<AbstractJakartarsProvider<?>>)s)
				.filter(not(AbstractJakartarsProvider::isFailed))
				.sorted()
				.collect(toUnmodifiableList());
			
		for(int i = 0; i < allCandidates.size(); i++) {
			AbstractJakartarsProvider<?> p = allCandidates.get(i);
			String name = p.getName();
			String id = p.getId();
			for(int j = i+1; j < allCandidates.size(); j++) {
				AbstractJakartarsProvider<?> p2 = allCandidates.get(j);	
				// If they have the same name and different services the latter fails
				// This can happen if the same service is a resource and an extension
				if(name.equals(p2.getName()) && !id.equals(p2.getId())) {
					logger.info("Adding failure " + p2.getId() + " with name " + p2.getName() + " compared with " + p.getId());
					p2.updateStatus(DTOConstants.FAILURE_REASON_DUPLICATE_NAME);						
				}
			}
		}
		
		return allCandidates.stream().filter(not(AbstractJakartarsProvider::isFailed)).collect(Collectors.toList());	
	}
	
	private void assignContent(Collection<JerseyApplicationProvider> candidates,
			Collection<? extends JerseyApplicationContentProvider> content) {
		
		// determine all content that match an application and returns the ones that found a match
		for(JerseyApplicationContentProvider jacp : content) {
			boolean matched = false;
			for(JerseyApplicationProvider jap : candidates) {
				if(jacp.canHandleApplication(jap)) {
					logger.info("Added content " + jacp.getName() + " to application " + jap.getName() + " " + jacp.getObjectClass());
					matched = true;
					if(!jap.addContent(jacp.cleanCopy())) {
						logger.warning("Unhandled JerseyApplicationContentProvider. Could not add content " + jacp + " to application " + jap.getName());
					}
				}
			}
			if(!matched) {
				jacp.updateStatus(DTOConstants.FAILURE_REASON_REQUIRED_APPLICATION_UNAVAILABLE);
			}
		}
	}
	
	/**
	 * Check the osgi.jakartars.extension.select for apps and extensions. 
	 * If an app requires an extension and this is not present, then all the extensions previously added to that app
	 * will be removed and the app itself will be unregistered and recorded as a failure DTO.
	 * If an extension requires another extension which is not present, the extension is removed from the app. In this
	 * case we then check recursively if the removal of such extension causes other extensions or the app itself to be
	 * unsatisfied.
	 * 
	 * @param applicationCandidates the app candidates
	 * @return the set of surviving apps after this check
	 */
	private List<JerseyApplicationProvider> checkExtensionSelect(List<JerseyApplicationProvider> applicationCandidates,
			List<JerseyExtensionProvider> extensionsForDTO, Map<String, Object> runtimeProperties) {
		
		
		for(JerseyApplicationProvider app : applicationCandidates) {
		
			Map<AbstractJakartarsProvider<?>, Set<String>> dependencyMap = new HashMap<AbstractJakartarsProvider<?>, Set<String>>();

			// get the extensions which have been added to this app
			Collection<JerseyApplicationContentProvider> contents = app.getContentProviders();
			List<JerseyExtensionProvider> extensions = contents.stream()
					.filter(JerseyExtensionProvider.class::isInstance)
					.map(JerseyExtensionProvider.class::cast)
					.collect(Collectors.toList());

			// check if the app itself requires some ext. If so, check if they are among the contents.
			// If not, the application should be put in the failed ones and all the ext should be removed from the app
			if(app.requiresExtensions()) {
				dependencyMap.put(app, new HashSet<String>());
				List<Filter> extFilters = app.getExtensionFilters();			
				
				filters: for(Filter filter : extFilters) {					
					for(JerseyExtensionProvider ext : extensions) {
						if(filter.matches(ext.getProviderProperties())) {
							dependencyMap.get(app).add(ext.getId());
							continue filters;
						}
					}
					if(!filter.matches(runtimeProperties)) {
						app.updateStatus(DTOConstants.FAILURE_REASON_REQUIRED_EXTENSIONS_UNAVAILABLE);
						break;
					}					
				}				
			}
//			We must check all the extensions for extension requirement
//			If a required ext is not there we remove the extension which was asking for it from the app
//			We also check if previous passing extension needed that ext. In that case we unregistered also
//			those ones recursively. If the app needed one of the removed extension we remove the app
			
			for(JerseyExtensionProvider ext : extensions) {
				if(ext.requiresExtensions()) {
					dependencyMap.put(ext, new HashSet<String>());
					List<Filter> extFilters = ext.getExtensionFilters();	
					filters: for(Filter filter : extFilters) {					
						for(JerseyExtensionProvider ext2 : extensions) {
							if(filter.matches(ext2.getProviderProperties())) {
								dependencyMap.get(ext).add(ext2.getId());
								continue filters;
							}
						}
						if(!filter.matches(app.getProviderProperties()) && 
								!filter.matches(runtimeProperties)) {
							// Remove this extension, mark as failed, add it to the DTO list
							handleExtensionRemoval(app, ext, extensionsForDTO, dependencyMap);		
						}				
					}
				}
			}			
		}	
		return applicationCandidates.stream().filter(not(AbstractJakartarsProvider::isFailed)).collect(toList());
	}
	
	private void handleExtensionRemoval(JerseyApplicationProvider app, JerseyExtensionProvider ext,
			List<JerseyExtensionProvider> extensionsForDTO, Map<AbstractJakartarsProvider<?>, Set<String>> dependencies) {
		app.removeContent(ext);
		ext.updateStatus(DTOConstants.FAILURE_REASON_REQUIRED_EXTENSIONS_UNAVAILABLE);
		extensionsForDTO.add(ext);
		// Check the dependencies to remove any other extensions that now need removing
		String id = ext.getId();
		dependencies.entrySet().stream()
			.filter(e -> e.getValue().contains(id))
			.map(Entry::getKey)
			.forEach(e -> {
				if (e instanceof JerseyExtensionProvider){
					handleExtensionRemoval(app, (JerseyExtensionProvider) e, extensionsForDTO, dependencies);
				} else {
					e.updateStatus(DTOConstants.FAILURE_REASON_REQUIRED_EXTENSIONS_UNAVAILABLE);	
				}
			});
	}
	
	/**
	 * Check extension dependencies for resources. If a dependency is not satisfied, the resource is 
	 * removed from the app. 
	 * 
	 * @param applicationCandidates
	 */
	private void checkExtensionSelectForResources(List<JerseyApplicationProvider> applicationCandidates,
			List<JerseyResourceProvider> resourcesForDTO, Map<String, Object> runtimeProperties) {
		
		for(JerseyApplicationProvider app : applicationCandidates) {

			Collection<JerseyApplicationContentProvider> contents = app.getContentProviders();
			
//			get the extensions which have been added to this app
			List<JerseyExtensionProvider> extensions = contents.stream()
					.filter(JerseyExtensionProvider.class::isInstance)
					.map(JerseyExtensionProvider.class::cast)
					.collect(Collectors.toList());
			
//			get the resources which have been added to this app
			List<JerseyResourceProvider> resources = contents.stream()
					.filter(JerseyResourceProvider.class::isInstance)
					.map(JerseyResourceProvider.class::cast)
					.collect(Collectors.toList());
			
			for(JerseyResourceProvider res : resources) {
				if(res.requiresExtensions()) {
					List<Filter> extFilters = res.getExtensionFilters();	
					filters: for(Filter filter : extFilters) {					
						for(JerseyExtensionProvider ext : extensions) {
							if(filter.matches(ext.getProviderProperties())) {
								continue filters;
							}
						}
						if(!filter.matches(app.getProviderProperties())
								&& !filter.matches(runtimeProperties)) {
							app.removeContent(res);
							res.updateStatus(DTOConstants.FAILURE_REASON_REQUIRED_EXTENSIONS_UNAVAILABLE);
							resourcesForDTO.add(res);
						}
					}
				}
			}			
		}
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.whiteboard.JakartarsWhiteboardProvider#teardown()
	 */
	public void teardown(long time, TimeUnit unit) {
		synchronized (lock) {
			active = Boolean.FALSE;
		}
		try {
			Future<?> f = executor.submit(() -> {
				if(regJakartarsServiceRuntime != null) {
					regJakartarsServiceRuntime.unregister();
					regJakartarsServiceRuntime = null;
				}
				containersByPath.entrySet().forEach(e -> containerDestroyer.accept(e.getKey(), e.getValue()));
			});
			executor.shutdown();
			f.get(time, TimeUnit.SECONDS);
		} catch (Exception e) {
			logger.severe(e.getMessage());
		}
	}


	private RuntimeDTO getUpdatedRuntimeDTO(Map<String, Object> properties, 
			List<JerseyApplicationProvider> applications, List<JerseyExtensionProvider> extensions, 
			List<JerseyResourceProvider> resources) {
		
		RuntimeDTO newDto = new RuntimeDTO();
		
		if(regJakartarsServiceRuntime != null) {
			newDto.serviceDTO = DTOConverter.toServiceReferenceDTO(regJakartarsServiceRuntime.getReference());
			newDto.serviceDTO.properties.putAll(properties);
		} else {
			newDto.serviceDTO = new ServiceReferenceDTO();
			newDto.serviceDTO.properties = properties;
			newDto.serviceDTO.bundle = context.getBundle().getBundleId();
			newDto.serviceDTO.id = -1;
			newDto.serviceDTO.usingBundles = new long[0];
		}
		
		List<ApplicationDTO> appDTOList = new ArrayList<>();
		List<FailedApplicationDTO> failedAppDTOList = new ArrayList<>();
		for(JerseyApplicationProvider jap : applications) {
			BaseApplicationDTO appDTO = jap.getApplicationDTO();
			if (appDTO instanceof ApplicationDTO) {
				ApplicationDTO curDTO = (ApplicationDTO) appDTO;
				if (curDTO.name.equals(".default") || curDTO.base.equals("/")) {
					newDto.defaultApplication = curDTO;
				} else {
					appDTOList.add(curDTO);
				}
			} else if (appDTO instanceof FailedApplicationDTO) {
				failedAppDTOList.add((FailedApplicationDTO) appDTO);
			} else {
				logger.severe("The application " + jap.getId() + " had an invalid dto.");
			}
		}
		
		newDto.applicationDTOs = appDTOList.toArray(ApplicationDTO[]::new);
		newDto.failedApplicationDTOs = failedAppDTOList.toArray(FailedApplicationDTO[]::new);
		
		List<FailedExtensionDTO> failedExtensions = new ArrayList<>();
		for(JerseyExtensionProvider jep : extensions) {
			BaseExtensionDTO dto = jep.getExtensionDTO();
			if(dto instanceof FailedExtensionDTO) {
				failedExtensions.add((FailedExtensionDTO) dto);
			}
		}
		newDto.failedExtensionDTOs = failedExtensions.toArray(FailedExtensionDTO[]::new);

		List<FailedResourceDTO> failedResources = new ArrayList<>();
		for(JerseyResourceProvider jrp : resources) {
			BaseDTO dto = jrp.getResourceDTO();
			if(dto instanceof FailedResourceDTO) {
				failedResources.add((FailedResourceDTO) dto);
			}
		}
		newDto.failedResourceDTOs = failedResources.toArray(FailedResourceDTO[]::new);
		
//		We need to add the ResourceDTO which uses NameBinding with the corresponding Extension, for all app plus the default one		
		setExtResourceForNameBinding(newDto.applicationDTOs);
		setExtResourceForNameBinding(new ApplicationDTO[] {newDto.defaultApplication});
		
		return newDto;
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

	/**
	 * Creates a new {@link ResourceConfig} for a given application. this method takes care of registering
	 * Jersey factories for prototype scoped resource services and singletons separately
	 * @param applicationProvider the Jakartars application application provider
	 */
	private ResourceConfig createResourceConfig(JerseyApplicationProvider applicationProvider) {
		if (applicationProvider == null) {
			logger.log(Level.WARNING, "Cannot create a resource configuration for null application provider");
			return null;
		}
		Application application = applicationProvider.getJakartarsApplication();
		ResourceConfig config = ResourceConfig.forApplication(application);
		config.setApplicationName(applicationProvider.getName());
		final Map<String, Object> properties = new HashMap<String, Object>(config.getProperties());
		properties.put(ServerProperties.RESOURCE_VALIDATION_IGNORE_ERRORS, Boolean.TRUE);
		config.setProperties(properties);
		
		PrototypeServiceBinder resBinder = new PrototypeServiceBinder();
		
		List<InjectableFactory<?>> factories = applicationProvider.getContentProviders().stream()
			.filter(JerseyResourceProvider.class::isInstance)
			.map(JerseyResourceProvider.class::cast)
			.map(provider -> {					
				logger.info("Register prototype provider for classes " + provider.getObjectClass() + " in the application " + applicationProvider.getId());
				logger.info("Register prototype provider for name " + provider.getName() + " id " + provider.getId() + " rank " + provider.getServiceRank());
				InjectableFactory<?> factory = new JerseyResourceInstanceFactory<>(provider);
				resBinder.register(provider.getObjectClass(), factory);
				return factory;
			})
			.collect(toList());
		config.register(resBinder);
		config.register(new ContainerLifecycleFeature(factories));
				
		return config;
	}
	
	private static class ContainerLifecycleFeature implements Feature {
		
		private final List<InjectableFactory<?>> factories;

		public ContainerLifecycleFeature(List<InjectableFactory<?>> factories) {
			this.factories = factories;
		}

		@Override
		public boolean configure(FeatureContext context) {
			InjectionManager im = InjectionManagerProvider.getInjectionManager(context);
			factories.forEach(factory -> factory.setInjectionManager(im));
			context.register(new AbstractContainerLifecycleListener() {
				@Override
				public void onShutdown(Container container) {
					Application application = container.getConfiguration().getApplication();
					// Try this one time in case we have a WrappingResourceConfig
					if(application instanceof ResourceConfig) {
						application = ((ResourceConfig)application).getApplication();
					}
					if(application instanceof JerseyApplication) {
						((JerseyApplication)application).dispose();
					}
				}
			});
			return true;
		}
	}
}
