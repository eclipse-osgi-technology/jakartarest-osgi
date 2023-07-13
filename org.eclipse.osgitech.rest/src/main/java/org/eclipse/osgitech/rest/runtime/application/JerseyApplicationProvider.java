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

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V11;
import static org.objectweb.asm.Type.VOID_TYPE;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_DEFAULT_APPLICATION;
import static org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants.JAKARTA_RS_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.helper.JakartarsHelper;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Implementation of the Application Provider
 * @author Mark Hoffmann
 * @since 30.07.2017
 */
public class JerseyApplicationProvider extends AbstractJakartarsProvider<Application> {

	private static final Logger logger = Logger.getLogger("jersey.applicationProvider");
	
	private JerseyApplication wrappedApplication;
	private boolean locked;
	private List<JerseyApplicationContentProvider> providers = new ArrayList<>();

	public JerseyApplicationProvider(Application application, Map<String, Object> properties) {
		super(application, properties);
	}

	private static class DynamicSubClassLoader extends ClassLoader {

		public DynamicSubClassLoader() {
			super(JerseyApplication.class.getClassLoader());
		}
		
		@SuppressWarnings("unchecked")
		public Class<? extends JerseyApplication> getSubClass(byte[] bytes) {
			return (Class<? extends JerseyApplication>) defineClass("org.eclipse.osgitech.rest.runtime.application.JerseyApplicationWithPath", bytes, 0, bytes.length);
		}
	}
	
	private JerseyApplication createDynamicSubclass(String name, Application application, Map<String, Object> properties) {

		ApplicationPath pathInfo = application.getClass().getAnnotation(ApplicationPath.class);
		String superName = getInternalName(JerseyApplication.class);

		// Write the class header, with JerseyApplication as the superclass
		ClassWriter writer = new ClassWriter(COMPUTE_FRAMES);
		writer.visit(V11, ACC_PUBLIC, "org/eclipse/osgitech/rest/runtime/application/JerseyApplicationWithPath", null, 
				superName, null);
		// Write the application path annotation
		AnnotationVisitor av = writer.visitAnnotation(getDescriptor(ApplicationPath.class), true);
		av.visit("value", pathInfo.value());
		av.visitEnd();
		
		// Write a constructor which directly calls super and nothing else
		String constructorDescriptor = getMethodDescriptor(VOID_TYPE, getType(String.class), 
				getType(Application.class), getType(Map.class), getType(List.class));
		
		MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitVarInsn(Opcodes.ALOAD, 4);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", constructorDescriptor, false);
		mv.visitInsn(RETURN);
		mv.visitMaxs(4, 4);
		mv.visitEnd();
		writer.visitEnd();
		
		DynamicSubClassLoader loader = new DynamicSubClassLoader();
		Class<? extends JerseyApplication> clazz = loader.getSubClass(writer.toByteArray());
		try {
			return clazz.getConstructor(String.class, Application.class, Map.class, List.class).newInstance(name, application, properties, providers);
		} catch (Exception e) {
			logger.severe("Unable to create a subclass of the JerseyApplication " + e.getMessage());
		}
		return null;
	}
	
	/** 
	 * Gets the full application path, including the osgi.jakartars.application.base and any
	 * {@link ApplicationPath} that is applied to the application
	 */
	public String getPath() {
		String base = getApplicationBase();
		return JakartarsHelper.getFullApplicationPath(getProviderObject(), base == null ? "" : base);
	}
	
	/** 
	 * Gets the wrapped whiteboard application suitable for deployment into Jersey
	 */
	public Application getJakartarsApplication() {
		locked = true;
		if (wrappedApplication == null) {
			Application application = getProviderObject();
			if (application.getClass().isAnnotationPresent(ApplicationPath.class)) {
				// Dynamic subclass with the annotation value
				wrappedApplication = createDynamicSubclass(getApplicationBase(), application, getProviderProperties());
			} else {
				wrappedApplication = new JerseyApplication(getProviderName(), application, getProviderProperties(), providers);
			}
		}
		return wrappedApplication;
	}

	/** 
	 * Get the DTO representing this provider
	 */
	public BaseApplicationDTO getApplicationDTO() {
		int status = getProviderStatus();
		if (status == NO_FAILURE) {
			return DTOConverter.toApplicationDTO(this);
		} else {
			return DTOConverter.toFailedApplicationDTO(this, status);
		}
	}

	/**
	 * Returns true if this is the default application
	 */
	public boolean isDefault() {
		return JAKARTA_RS_DEFAULT_APPLICATION.equals(getName());
	}
	
	/** 
	 * Returns true if this is shadowing the default application
	 */
	public boolean isShadowDefault() {
		return "/".equals(getApplicationBase()) && !isDefault();
	}

	/** 
	 * Add a resource provider to this application so that it can be used
	 */
	public boolean addContent(JerseyApplicationContentProvider provider) {
		if (locked) {
			throw new IllegalStateException("The application " + getId() + " (" + getName() + ") is locked");
		}
		if (provider.isFailed()) {
			logger.log(Level.WARNING, "The resource to add is not valid: " + provider.getProviderStatus());
			return false;
		}
		return providers.add(provider);
	}

	/** 
	 * Remove a resource from this application, used as part of validating extension selection
	 */
	public boolean removeContent(JerseyApplicationContentProvider provider) {
		if (locked) {
			throw new IllegalStateException("The application " + getId() + " (" + getName() + ") is locked");
		}
		if (provider == null) {
			logger.log(Level.WARNING, "The resource provider is null. There is nothing to remove.");
			return false;
		}
		return providers.remove(provider);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.AbstractJakartarsProvider#getProviderName()
	 */
	@Override
	protected String getProviderName() {
		String name = null;
		Map<String, Object> providerProperties = getProviderProperties();
		if (providerProperties != null) {
			String baseProperty = getPath();
			name = (String) providerProperties.get(JAKARTA_RS_NAME);
			if (name == null && baseProperty != null) {
				name = "." + baseProperty;
			} else if (name != null && !name.equals(".default") && (name.startsWith(".") || name.startsWith("osgi"))) {
				updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			}
		}
		return name == null ? calculateProviderId() : name;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.AbstractJakartarsProvider#doValidateProperties(java.util.Map)
	 */
	@Override
	protected void doValidateProperties(Map<String, Object> properties) {
		String applicationBase = getApplicationBase();
		if (applicationBase == null || applicationBase.isEmpty()) {
			updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			return;
		}
	}

	private String getApplicationBase() {
		return (String) getProviderProperties().get(JAKARTA_RS_APPLICATION_BASE);
	}
	
	/**
	 * Return the content providers known to this application
	 */
	public Collection<JerseyApplicationContentProvider> getContentProviders() {
		return List.copyOf(providers);
	}

	/**
	 * Return true if t
	 * @param application
	 * @return
	 */
	public boolean isChanged(Application application) {
		return true;
	}

	@Override
	public JerseyApplicationProvider cleanCopy() {
		return new JerseyApplicationProvider(getProviderObject(), getProviderProperties());
	}

}
