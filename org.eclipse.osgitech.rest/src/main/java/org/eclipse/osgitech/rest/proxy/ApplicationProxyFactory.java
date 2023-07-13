/**
 * Copyright (c) 2012 - 2023 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Tim Ward - implementation
 */
package org.eclipse.osgitech.rest.proxy;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V11;
import static org.objectweb.asm.Type.VOID_TYPE;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

import java.util.List;
import java.util.Map;

import org.eclipse.osgitech.rest.runtime.application.JerseyApplication;
import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationContentProvider;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * This class is used to generate a proxy for an application service
 * 
 * The proxy will:
 * 
 * * Extend {@link JerseyApplication}
 * * Copy the {@link ApplicationPath} annotation from the application
 * * Have a single delegating constructor
 * 
 * 
 * @author timothyjward
 * @since 13 Jul 2023
 */
public class ApplicationProxyFactory {
	
	private static class DynamicSubClassLoader extends ClassLoader {

		public DynamicSubClassLoader() {
			super(JerseyApplication.class.getClassLoader());
		}
		
		@SuppressWarnings("unchecked")
		public Class<? extends JerseyApplication> getSubClass(byte[] bytes) {
			return (Class<? extends JerseyApplication>) defineClass("org.eclipse.osgitech.rest.runtime.application.JerseyApplicationWithPath", bytes, 0, bytes.length);
		}
	}
	
	/**
	 * Create a dynamic subclass instance
	 * @param name - the application name
	 * @param application - the source application, including the {@link ApplicationPath} annotation to copy
	 * @param properties - the service properties
	 * @param providers - the content providers for this application
	 * @return
	 */
	public static JerseyApplication createDynamicSubclass(String name, Application application, Map<String, Object> properties,
			List<JerseyApplicationContentProvider> providers) {

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
			throw new IllegalArgumentException(e);
		}
	}
	
}
