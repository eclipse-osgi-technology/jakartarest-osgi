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
package org.eclipse.osgitech.rest.dto;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationContentProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplication;
import org.eclipse.osgitech.rest.runtime.application.JerseyExtensionProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyResourceProvider;
import org.eclipse.osgitech.rest.runtime.application.feature.WhiteboardFeature;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.BaseDTO;
import org.osgi.service.jakartars.runtime.dto.ExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.FailedExtensionDTO;
import org.osgi.service.jakartars.runtime.dto.FailedResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceMethodInfoDTO;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.NameBinding;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.WriterInterceptor;


/**
 * Helper class to convert object into DTO's
 * @author Mark Hoffmann
 * @since 14.07.2017
 */
public class DTOConverter {
	
	private static final List<String> POSSIBLE_EXTENSION_INTERFACES = Arrays.asList(new String[] {
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
	
	private static final String WHITEBOARD_FEATURE = WhiteboardFeature.class.getName();
	
	/**
	 * This mapping sequence was taken from:
	 * @see https://github.com/njbartlett/osgi_jigsaw/blob/master/nbartlett-jigsaw-osgi/src/nbartlett.jigsaw_osgi/org/apache/felix/framework/DTOFactory.java
	 * @param svc the service reference
	 * @return the service reference dto
	 */
	public static ApplicationDTO toApplicationDTO(JakartarsApplicationProvider applicationProvider) {
		if (applicationProvider == null) {
			throw new IllegalArgumentException("Expected an application provider to create an ApplicationDTO");
		}
		ApplicationDTO dto = new JerseyApplicationDTO();
		dto.name = applicationProvider.getName();
		String basePath = applicationProvider.getPath();
		if(basePath!=null) {
			dto.base = basePath.replaceAll("/\\*", "/");	
		}
	
		Long sid = applicationProvider.getServiceId();
		dto.serviceId = sid != null ? sid.longValue() : -1;

		// Search for contentProvider and generate ResourceDTOs and ExtensionDTOs
		List<ResourceDTO> rdtos = new ArrayList<>();
		List<ResourceMethodInfoDTO> rmidtos = new ArrayList<>();
		List<ExtensionDTO> edtos = new ArrayList<>();
		
//		Create the DTO for the static resources and extensions 
		if(applicationProvider.getJakartarsApplication() instanceof JerseyApplication) {
			Application sourceApp = ((JerseyApplication) applicationProvider.getJakartarsApplication()).getSourceApplication();
			Set<Object> singletons = sourceApp.getSingletons();
			Set<Class<?>> classes = sourceApp.getClasses();
			for(Object obj : singletons) {
				if(WHITEBOARD_FEATURE.equals(obj.getClass().getName())) {
					continue;
				}
				BaseDTO baseDTO = toDTO(obj.getClass());
				if(baseDTO instanceof ResourceDTO) {
					rdtos.add(((ResourceDTO) baseDTO));
					rmidtos.addAll(Arrays.asList(((ResourceDTO) baseDTO).resourceMethods));
					
				}
				else {
					edtos.add((ExtensionDTO) baseDTO);
				}
				
			}
			for(Class<?> c : classes) {
				if(WHITEBOARD_FEATURE.equals(c.getName())) {
					continue;
				}
				BaseDTO baseDTO = toDTO(c);
				if(baseDTO instanceof ResourceDTO) {
					rdtos.add(((ResourceDTO) baseDTO));
					rmidtos.addAll(Arrays.asList(((ResourceDTO) baseDTO).resourceMethods));
				}
				else {
					edtos.add((ExtensionDTO) baseDTO);
				}
			}	
		}
			
		
		if (applicationProvider.getContentProviers() != null) {

			for (JakartarsApplicationContentProvider contentProvider : applicationProvider.getContentProviers()) {

				if (contentProvider instanceof JerseyResourceProvider) {
					ResourceDTO resDTO = toResourceDTO((JakartarsResourceProvider) contentProvider);
					rdtos.add(resDTO);
					if(resDTO.resourceMethods != null) {
						rmidtos.addAll(Arrays.asList(resDTO.resourceMethods));
					}					
				} else if (contentProvider instanceof JerseyExtensionProvider) {
					edtos.add(toExtensionDTO((JerseyExtensionProvider<?>) contentProvider));
				}
			}
		}
		
		dto.resourceDTOs = rdtos.toArray(new ResourceDTO[rdtos.size()]);
		dto.extensionDTOs = edtos.toArray(new ExtensionDTO[edtos.size()]);		
		dto.resourceMethods = rmidtos.toArray(new ResourceMethodInfoDTO[rmidtos.size()]);
		return dto;
	}
	
	/**
	 * This mapping sequence was taken from:
	 * @see https://github.com/njbartlett/osgi_jigsaw/blob/master/nbartlett-jigsaw-osgi/src/nbartlett.jigsaw_osgi/org/apache/felix/framework/DTOFactory.java
	 * @param svc the service reference
	 * @return the service reference dto
	 */
	public static FailedApplicationDTO toFailedApplicationDTO(JakartarsApplicationProvider applicationProvider, int reason) {
		if (applicationProvider == null) {
			throw new IllegalArgumentException("Expected an application provider to create a FailedApplicationDTO");
		}
		FailedApplicationDTO dto = new FailedApplicationDTO();
		dto.name = applicationProvider.getName();
		dto.base = applicationProvider.getPath();
		Long sid = applicationProvider.getServiceId();
		dto.serviceId = sid != null ? sid.longValue() : -1; 
		dto.failureReason = reason;
		return dto;
	}

	/**
	 * Maps a {@link JakartarsResourceProvider} into a {@link ResourceDTO}
	 * @param resourceProvider the resource provider instance, needed to be inspect
	 * @return a {@link ResourceDTO} or <code>null</code>, if the given object is no Jakartars resource
	 */
	public static <T> ResourceDTO toResourceDTO(JakartarsResourceProvider resourceProvider) {
		if (resourceProvider == null) {
			throw new IllegalArgumentException("Expected an resource provider to create an ResourceDTO");
		}
		ResourceDTO dto = new JerseyResourceDTO();
		dto.name = resourceProvider.getName();
		Long serviceId = resourceProvider.getServiceId();
		dto.serviceId = -1;
		if (serviceId != null) {
			dto.serviceId = serviceId.longValue();
		} 
		ResourceMethodInfoDTO[] rmiDTOs = getResourceMethodInfoDTOs(resourceProvider.getObjectClass());
		if (rmiDTOs != null) {
			dto.resourceMethods = rmiDTOs;
		}
		return dto;
	}
	
	
	
	/**
	 * This creates a DTO for resources and extensions which have been added to an application in a 
	 * static way. To check whether to create a ResourceDTO or an ExtensionDTO, it looks if some 
	 * ResourceMethodInfo can be created. If yes, then the clazz is treated as a Resource and the 
	 * ResourceDTO is created, otherwise the clazz is treated as an Extension and the ExtensionDTO
	 * is created
	 * 
	 * @param <T>
	 * @param clazz
	 * @return
	 */
	public static <T> BaseDTO toDTO(Class<?> clazz) {
		
		BaseDTO dto = null;
		ResourceMethodInfoDTO[] rmiDTOs = getResourceMethodInfoDTOs(clazz);
		if(rmiDTOs != null) {
//			We have a Resource
			ResourceDTO resDTO = new ResourceDTO();
			resDTO.name = clazz.getName();
			resDTO.resourceMethods = rmiDTOs;
			dto = resDTO;
		}
		else {
//			We have an Extension
			ExtensionDTO extDTO = new ExtensionDTO();
			extDTO.name = clazz.getName();
			List<String> extTypeList = new LinkedList<String>();
			for(Class<?> c : clazz.getInterfaces()) {
				if(POSSIBLE_EXTENSION_INTERFACES.contains(c.getName())) {
					extTypeList.add(c.getName());
				}
			}
			extDTO.extensionTypes = extTypeList.toArray(new String[0]);
			extDTO = toExtensionDTO(clazz, extDTO);
			dto = extDTO;			
		}		
		return dto;
	}
	
	/**
	 * Maps resource provider into a {@link FailedResourceDTO}
	 * @param resourceProvider the resource provider instance, needed to be inspect
	 * @param reason the error reason
	 * @return a {@link FailedResourceDTO} or <code>null</code>, if the given object is no Jakartars resource
	 */
	public static FailedResourceDTO toFailedResourceDTO(JakartarsResourceProvider resourceProvider, int reason) {
		if (resourceProvider == null) {
			throw new IllegalArgumentException("Expected an resource provider to create an FailedResourceDTO");
		}
		FailedResourceDTO dto = new FailedResourceDTO();
		dto.name = resourceProvider.getName();
		Long serviceId = resourceProvider.getServiceId();
		dto.serviceId = serviceId != null ? serviceId.longValue() : -1;
		dto.failureReason = reason;
		return dto;
	}
	
	/**
	 * Maps a {@link JakartarsExtensionProvider} into a {@link ExtensionDTO}
	 * @param provider the extension provider instance, needed to be inspect
	 * @return a {@link ExtensionDTO} or <code>null</code>, if the given object is no Jakartars extension
	 */
	public static <T> ExtensionDTO toExtensionDTO(JakartarsExtensionProvider provider) {
		if (provider == null) {
			throw new IllegalArgumentException("Expected an application content provider to create an ExtensionDTO");
		}
		ExtensionDTO dto = new JerseyExtensionDTO();
		Class<?> clazz = provider.getObjectClass();
		dto = toExtensionDTO(clazz, dto);
		Class<?>[] contracts = provider.getContracts() == null ? new Class<?>[0] : provider.getContracts();
 		String[] extTypes = new String[contracts.length];
		for(int c = 0; c < contracts.length; c++) {
			extTypes[c] = contracts[c].getName();
		}
		dto.extensionTypes = extTypes;
		
		dto.name = provider.getName();
		Long serviceId = provider.getServiceId();
		dto.serviceId = -1;
		if (serviceId != null) {
			dto.serviceId = serviceId.longValue();
		} 		
		return dto;
	}
	
	private static <T> ExtensionDTO toExtensionDTO(Class<?> clazz, ExtensionDTO dto) {
		List<String> nbList = new LinkedList<String>();
		for(Class<?> dc : clazz.getDeclaredClasses()) {
			NameBinding nb = dc.getAnnotation(NameBinding.class);
			if(nb != null) {
				if(!nbList.contains(dc.getName())) {
					nbList.add(dc.getName());	
				}				
			}
		}		
		if(nbList.size() > 0) {
			dto.nameBindings = nbList.toArray(new String[0]);
		}		
		
		Produces produces = clazz.getAnnotation(Produces.class);
		if (produces != null) {
			dto.produces = produces.value();
		}
		Consumes consumes = clazz.getAnnotation(Consumes.class);
		if (consumes != null) {
			dto.consumes = consumes.value();
		}
		return dto;
	}
	
	/**
	 * Maps resource provider into a {@link FailedExtensionDTO}
	 * @param provider the extension provider instance, needed to be inspect
	 * @param reason the error reason
	 * @return a {@link FailedExtensionDTO} or <code>null</code>, if the given object is no Jakartars extension
	 */
	public static FailedExtensionDTO toFailedExtensionDTO(JakartarsExtensionProvider provider, int reason) {
		if (provider == null) {
			throw new IllegalArgumentException("Expected an application content provider to create an FailedExtensionDTO");
		}
		FailedExtensionDTO dto = new FailedExtensionDTO();
		dto.name = provider.getName();
		Long serviceId = provider.getServiceId();
		dto.serviceId = serviceId != null ? serviceId.longValue() : -1;
		dto.failureReason = reason;
		return dto;
	}

	/**
	 * Creates an array of {@link ResourceMethodInfoDTO} from a given object. A object will only be created,
	 * if at least one of the fields is set.
	 * @param resource the object class to parse
	 * @return an array of method objects or <code>null</code>
	 */
	public static <T> ResourceMethodInfoDTO[] getResourceMethodInfoDTOs(Class<T> clazz) {
		Path resPath = clazz.getAnnotation(Path.class);
		Consumes resConsumes = clazz.getAnnotation(Consumes.class);
		Produces resProduces = clazz.getAnnotation(Produces.class);
		return Arrays.stream(clazz.getMethods())
				.map(m -> toResourceMethodInfoDTO(m, resPath, resProduces, resConsumes))
				.filter(Objects::nonNull)
				.toArray(ResourceMethodInfoDTO[]::new);
	}

	/**
	 * Creates a {@link ResourceMethodInfoDTO} from a given {@link Method}. An object will only be created,
	 * if at least one of the fields is set.
	 * @param method the {@link Method} to parse
	 * @param resPath the resource level path
	 * @param resProduces the resource level produces
	 * @param resConsumes the resource level consumes
	 * @return an DTO or <code>null</code>
	 */
	public static <T> ResourceMethodInfoDTO toResourceMethodInfoDTO(Method method, Path resPath,
			Produces resProduces, Consumes resConsumes) {
		if (method == null) {
			throw new IllegalArgumentException("Expected a method instance to introspect annpotations and create a ResourceMethodInfoDTO");
		}
		ResourceMethodInfoDTO dto = new ResourceMethodInfoDTO();

		String methodString = getMethodStrings(method);
		if (methodString != null) {
			dto.method = methodString;
		} else {
			return null;
		}
		
//		Added nameBindings to ResourceDTO
		String[] bindings = Arrays.stream(method.getAnnotations())
			.filter(a -> a.annotationType().isAnnotationPresent(NameBinding.class))
			.map(a -> a.annotationType().getName())
			.distinct()
			.toArray(String[]::new);
		dto.nameBindings = bindings.length == 0 ? null : bindings;
		
		Consumes consumes = Optional.ofNullable(method.getAnnotation(Consumes.class))
				.orElse(resConsumes);
		if (consumes != null) {
			dto.consumingMimeType = consumes.value();
		}
		
		Produces produces = Optional.ofNullable(method.getAnnotation(Produces.class))
				.orElse(resProduces);
		if (produces != null) {
			dto.producingMimeType = produces.value();
		}
		
		Path path = method.getAnnotation(Path.class);
		if (path != null) {		
			if(resPath != null) {
				dto.path = resPath.value() + "/" + path.value();
			}
			else {
				dto.path = path.value();
			}			
		}
		else if(resPath != null && methodString != null) {
			dto.path = resPath.value();
		} else {
			dto.path = "";
		}
		
		return dto;
	}

	/**
	 * Parses the given method for a Jakartars method annotation. If the method is annotated with more than one
	 * method annotation, the values are separated by , 
	 * @param method the method instance of the class to be parsed.
	 * @return the HTTP method string or <code>null</code>
	 */
	public static String getMethodStrings(Method method) {
		List<String> methods = new LinkedList<>();
		checkMethodString(method, GET.class, methods);
		checkMethodString(method, POST.class, methods);
		checkMethodString(method, PUT.class, methods);
		checkMethodString(method, DELETE.class, methods);
		checkMethodString(method, HEAD.class, methods);
		checkMethodString(method, OPTIONS.class, methods);
		if (methods.isEmpty()) {
			return null;
		}
		return methods.stream().collect(Collectors.joining(","));
	}

	/**
	 * Checks a given annotation for presence on the method and add it to the result list
	 * @param method the method to be checked
	 * @param type the annotation type
	 * @param resultList the result list
	 */
	public static <T extends Annotation> void checkMethodString(Method method, Class<T> type, List<String> resultList) {
		T annotation = method.getAnnotation(type);
		if (annotation != null) {
			resultList.add(type.getSimpleName().toUpperCase());
		}
	}

	/**
	 * This mapping sequence was taken from:
	 * @see https://github.com/njbartlett/osgi_jigsaw/blob/master/nbartlett-jigsaw-osgi/src/nbartlett.jigsaw_osgi/org/apache/felix/framework/DTOFactory.java
	 * @param svc the service reference
	 * @return the service reference dto
	 */
	public static ServiceReferenceDTO toServiceReferenceDTO(ServiceReference<?> svc) {
		ServiceReferenceDTO dto = new ServiceReferenceDTO();
		dto.bundle = svc.getBundle().getBundleId();
		dto.id = (Long) svc.getProperty(Constants.SERVICE_ID);
		Map<String, Object> props = new HashMap<String, Object>();
		for (String key : svc.getPropertyKeys()) {
			props.put(key, svc.getProperty(key));
		}
		dto.properties = new HashMap<String, Object>(props);

		Bundle[] ubs = svc.getUsingBundles();
		if (ubs == null)
		{
			dto.usingBundles = new long[0];
		}
		else
		{
			dto.usingBundles = new long[ubs.length];
			for (int j=0; j < ubs.length; j++)
			{
				dto.usingBundles[j] = ubs[j].getBundleId();
			}
		}
		return dto;
	}

}
