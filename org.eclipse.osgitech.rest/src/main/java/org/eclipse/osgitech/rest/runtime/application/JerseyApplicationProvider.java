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
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V11;
import static org.objectweb.asm.Type.VOID_TYPE;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgitech.rest.dto.DTOConverter;
import org.eclipse.osgitech.rest.helper.JakartarsHelper;
import org.eclipse.osgitech.rest.helper.JerseyHelper;
import org.eclipse.osgitech.rest.provider.application.AbstractJakartarsProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationContentProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider;
import org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.osgi.service.jakartars.runtime.dto.BaseApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.DTOConstants;
import org.osgi.service.jakartars.runtime.dto.FailedApplicationDTO;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Implementation of the Application Provider
 * @author Mark Hoffmann
 * @since 30.07.2017
 */
public class JerseyApplicationProvider extends AbstractJakartarsProvider<Application> implements JakartarsApplicationProvider {

	private static final Logger logger = Logger.getLogger("jersey.applicationProvider");
	private String applicationBase;
	private final JerseyApplication wrappedApplication;

	public JerseyApplicationProvider(Application application, Map<String, Object> properties) {
		super(application, properties);
		// create name after validation, because some fields are needed eventually
		if(application == null) {
			wrappedApplication = null;
		} else if (application.getClass().isAnnotationPresent(ApplicationPath.class)) {
			// Dynamic subclass with the annotation value
			wrappedApplication = createDynamicSubclass(applicationBase, application, properties);
		} else {
			wrappedApplication = new JerseyApplication(getProviderName(), application, properties);
			
		}
		validateProperties();
	}

	private static class DynamicSubClassLoader extends ClassLoader {

		public DynamicSubClassLoader() {
			super(JerseyApplication.class.getClassLoader());
		}
		
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
				getType(Application.class), getType(Map.class));
		
		MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", constructorDescriptor, false);
		mv.visitInsn(RETURN);
		mv.visitMaxs(4, 4);
		mv.visitEnd();
		writer.visitEnd();
		
		DynamicSubClassLoader loader = new DynamicSubClassLoader();
		Class<? extends JerseyApplication> clazz = loader.getSubClass(writer.toByteArray());
		try {
			return clazz.getConstructor(String.class, Application.class, Map.class).newInstance(name, application, properties);
		} catch (Exception e) {
			logger.severe("Unable to create a subclass of the JerseyApplication " + e.getMessage());
		}
		return null;
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getPath()
	 */
	@Override
	public String getPath() {
		if (wrappedApplication == null) {
			throw new IllegalStateException("This application provider does not contain an application, but should have one to create a context path");
		}
		return JakartarsHelper.getFullApplicationPath(wrappedApplication.getSourceApplication(), applicationBase == null ? "" : applicationBase);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getJakartarsApplication()
	 */
	@Override
	public Application getJakartarsApplication() {
		if (wrappedApplication == null) {
			throw new IllegalStateException("This application provider does not contain an application, but should have one to return an application");
		}
		return wrappedApplication;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getApplicationProperties()
	 */
	@Override
	public Map<String, Object> getApplicationProperties() {
		return getProviderProperties();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getApplicationDTO()
	 */
	@Override
	public BaseApplicationDTO getApplicationDTO() {
		int status = getProviderStatus();
		if (wrappedApplication == null) {
			throw new IllegalStateException("This application provider does not contain an application, but should have one to get a DTO");
		}
		if (status == NO_FAILURE) {
			return DTOConverter.toApplicationDTO(this);
		} else {
			return DTOConverter.toFailedApplicationDTO(this, status);
		}
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#isDefault()
	 */
	public boolean isDefault() {
		return JakartarsWhiteboardConstants.JAKARTA_RS_DEFAULT_APPLICATION.equals(getName());
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#isShadowDefault()
	 */
	@Override
	public boolean isShadowDefault() {
		return "/".equals(applicationBase) && !isDefault();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return JerseyHelper.isEmpty(wrappedApplication);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#addResource(org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider)
	 */
	@Override
	public boolean addResource(JakartarsResourceProvider provider) {
		if (!provider.isResource()) {
			logger.log(Level.WARNING, "The resource to add is not declared with the resource property: " + JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE);
			return false;
		}
		return doAddContent(provider);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#removeResource(org.eclipse.osgitech.rest.provider.application.JakartarsResourceProvider)
	 */
	@Override
	public boolean removeResource(JakartarsResourceProvider provider) {
		if (provider == null) {
			logger.log(Level.WARNING, "The resource provider is null. There is nothing to remove.");
			return false;
		}
		if (!provider.isResource()) {
			logger.log(Level.WARNING, "The resource to be removed is not declared with the resource property: " + JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE);
			return false;
		}
		return doRemoveContent(provider);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#addExtension(org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider)
	 */
	@Override
	public boolean addExtension(JakartarsExtensionProvider provider) {
		if (!provider.isExtension()) {
			logger.log(Level.WARNING, "The extension to add is not declared with the extension property: " + JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION);
			return false;
		}
		return doAddContent(provider);
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#removeExtension(org.eclipse.osgitech.rest.provider.application.JakartarsExtensionProvider)
	 */
	@Override
	public boolean removeExtension(JakartarsExtensionProvider provider) {
		if (provider == null) {
			logger.log(Level.WARNING, "The extension provider is null. There is nothing to remove.");
			return false;
		}
		if (!provider.isExtension()) {
			logger.log(Level.WARNING, "The extension to be removed is not declared with the extension property: " + JakartarsWhiteboardConstants.JAKARTA_RS_EXTENSION);
			return false;
		}
		return doRemoveContent(provider);
	}
	
	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		return new JerseyApplicationProvider(getProviderObject(), getProviderProperties());
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
			String baseProperty = (String) providerProperties.get(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE);
			if (wrappedApplication != null) {
				baseProperty = getPath();
			}
			name = (String) providerProperties.get(JakartarsWhiteboardConstants.JAKARTA_RS_NAME);
			if (name == null && baseProperty != null) {
				name = "." + baseProperty;
			} else if (name != null && !name.equals(".default") && (name.startsWith(".") || name.startsWith("osgi"))) {
				updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			}
		}
		return name == null ? getProviderId() : name;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.AbstractJakartarsProvider#doValidateProperties(java.util.Map)
	 */
	@Override
	protected void doValidateProperties(Map<String, Object> properties) {
		String baseProperty = (String) properties.get(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE);
		if (applicationBase == null && (baseProperty == null || baseProperty.isEmpty())) {
			updateStatus(DTOConstants.FAILURE_REASON_VALIDATION_FAILED);
			return;
		}
		if (baseProperty != null && !baseProperty.isEmpty()) {
			applicationBase = baseProperty;
		} 
	}

	/**
	 * Adds content to the underlying {@link JerseyApplication}, if valid
	 * @param provider the content provider to be added
	 * @return <code>true</code>, if add was successful, otherwise <code>false</code>
	 */
	private boolean doAddContent(JakartarsApplicationContentProvider provider) {
		if(getApplicationDTO() instanceof FailedApplicationDTO) {
			return false;
		}
		return wrappedApplication.addContent(provider);
	}
	
	/**
	 * Removed content from the underlying {@link JerseyApplication}, if valid
	 * @param provider the content provider to be removed
	 * @return <code>true</code>, if removal was successful, otherwise <code>false</code>
	 */
	private boolean doRemoveContent(JakartarsApplicationContentProvider provider) {
		return wrappedApplication.removeContent(provider);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#getContentProviers()
	 */
	@Override
	public Collection<JakartarsApplicationContentProvider> getContentProviders() {
		return wrappedApplication.getContentProviders();
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.osgitech.rest.provider.application.JakartarsApplicationProvider#updateApplicationBase(java.lang.String)
	 */
	public void updateApplicationBase(String applicationBase) {
		doValidateProperties(Collections.singletonMap(JakartarsWhiteboardConstants.JAKARTA_RS_APPLICATION_BASE, applicationBase));
	}

	@Override
	public boolean isChanged(Application application) {
		// TODO optimise this by checking to see if the underlying application is the same
		return true;
	}

	@Override
	public JakartarsApplicationProvider cleanCopy() {
		return new JerseyApplicationProvider(getProviderObject(), getProviderProperties());
	}

}
