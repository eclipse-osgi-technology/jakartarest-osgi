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
package org.eclipse.osgitech.rest.proxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

/**
 * This class is used to generate proxies for extension services
 * 
 * The proxy will:
 * 
 * * Implement all of the contract interfaces
 * * Have the same generic signature for each interface implemented
 * * Extend Object
 * * Delegate to 
 * 
 * 
 * @author timothyjward
 * @since 9 May 2022
 */
public class ExtensionProxyFactory {
	
	private static final String OBJECT_INTERNAL_NAME = Type.getInternalName(Object.class);

	/**
	 * @param simpleName
	 */
	public static byte[] generateClass(String className, Object delegate, List<Class<?>> contracts) {
		Map<String, ParameterizedType> typeInfo = Arrays.stream(delegate.getClass().getGenericInterfaces())
				.filter(ParameterizedType.class::isInstance)
				.map(ParameterizedType.class::cast)
				.collect(Collectors.toMap(i -> i.getRawType().getTypeName(), Function.identity()));
		
		
		String sig = generateGenericClassSignature(typeInfo, contracts);
		
		
		try {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			
			String internalName = className.replace('.', '/');
			cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, 
					sig, OBJECT_INTERNAL_NAME, 
					contracts.stream()
						.map(Type::getInternalName)
						.toArray(String[]::new));
			
			for (Annotation annotation : delegate.getClass().getAnnotations()) {
				AnnotationVisitor av = cw.visitAnnotation(Type.getDescriptor(annotation.annotationType()), true);
				visitAnnotationMembers(annotation, av);
				av.visitEnd();
			}
		
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "delegateSupplier", Type.getDescriptor(Supplier.class), null, null).visitEnd();
			
			MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/util/function/Supplier;)V", null, null);
			constructor.visitCode();
			constructor.visitVarInsn(Opcodes.ALOAD, 0);
			constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, OBJECT_INTERNAL_NAME, 
					"<init>", "()V", false);
			constructor.visitVarInsn(Opcodes.ALOAD, 0);
			constructor.visitVarInsn(Opcodes.ALOAD, 1);
			constructor.visitFieldInsn(Opcodes.PUTFIELD, internalName, "delegateSupplier", Type.getDescriptor(Supplier.class));
			constructor.visitInsn(Opcodes.RETURN);
			constructor.visitMaxs(2, 2);
			constructor.visitEnd();
			
			for(Class<?> contract : contracts) {
				for(Method m : contract.getMethods()) {
					MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, m.getName(), Type.getMethodDescriptor(m), null, 
							Arrays.stream(m.getExceptionTypes()).map(Type::getInternalName).toArray(String[]::new));
					mv.visitCode();
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitFieldInsn(Opcodes.GETFIELD, internalName, "delegateSupplier", Type.getDescriptor(Supplier.class));
					mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(Supplier.class), "get", 
							Type.getMethodDescriptor(Supplier.class.getMethod("get")), true);
					mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(contract));
					for(int i = 0; i < m.getParameterCount(); i++) {
						mv.visitVarInsn(Opcodes.ALOAD, i + 1);
					}
					mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(contract), m.getName(), 
							Type.getMethodDescriptor(m), true);
					if(m.getReturnType().equals(Void.TYPE)) {
						mv.visitInsn(Opcodes.RETURN);
					} else if (m.getReturnType() == boolean.class || m.getReturnType() == byte.class || 
							m.getReturnType() == char.class || m.getReturnType() == int.class) {
						mv.visitInsn(Opcodes.IRETURN);
					} else if (m.getReturnType() == long.class ) {
						mv.visitInsn(Opcodes.LRETURN);
					} else if (m.getReturnType() == float.class ) {
						mv.visitInsn(Opcodes.FRETURN);
					} else if (m.getReturnType() == double.class ) {
						mv.visitInsn(Opcodes.DRETURN);
					} else {
						mv.visitInsn(Opcodes.ARETURN);
					}
					mv.visitMaxs(m.getParameterCount() + 1, m.getParameterCount() + 1);
					mv.visitEnd();
				}
			}
			
			cw.visitEnd();
			
			return cw.toByteArray();
		
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param typeInfo
	 * @return
	 */
	private static String generateGenericClassSignature(Map<String, ParameterizedType> typeInfo, List<Class<?>> contracts) {
		if(typeInfo.isEmpty()) {
			return null;
		}
		
		SignatureWriter sw = new SignatureWriter();
		// Handle parent
		SignatureVisitor sv = sw.visitSuperclass();
		sv.visitClassType(OBJECT_INTERNAL_NAME);
		sv.visitEnd();
		
		// Handle interfaces
		for(Class<?> contract : contracts) {
			if(typeInfo.containsKey(contract.getName())) {
				SignatureVisitor iv = sw.visitInterface();
				iv.visitClassType(Type.getInternalName(contract));
				for(java.lang.reflect.Type t : typeInfo.get(contract.getName()).getActualTypeArguments()) {
					visitTypeParameter(t, iv);
				}
				iv.visitEnd();
			}
		}
		 
		sw.visitEnd();
		return sw.toString();
	}

	private static void visitTypeParameter(java.lang.reflect.Type t, SignatureVisitor sv) {
		SignatureVisitor pv = sv.visitTypeArgument('=');
		if(t instanceof Class<?>) {
			Class<?> clazz = (Class<?>) t;
			if(clazz.isPrimitive()) {
				pv.visitBaseType(Type.getDescriptor(clazz).charAt(0));
			} else if (clazz.isArray()) {
				SignatureVisitor av = pv.visitArrayType();
				visitTypeParameter(clazz.getComponentType(), av);
				av.visitEnd();
			} else {
				pv.visitClassType(Type.getInternalName(clazz));
			}
		} else if (t instanceof ParameterizedType){
			ParameterizedType pt = (ParameterizedType) t;
			pv.visitClassType(Type.getInternalName((Class<?>)pt.getRawType()));
			Arrays.stream(pt.getActualTypeArguments()).forEach(ta -> visitTypeParameter(ta, pv));
		}
		pv.visitEnd();
	}
	
	private static void visitAnnotationMembers(Annotation a, AnnotationVisitor av) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		for (Method method : a.annotationType().getDeclaredMethods()) {
			Class<?> returnType = method.getReturnType();
			if(returnType.isAnnotation()) {
				AnnotationVisitor av2 = av.visitAnnotation(method.getName(), Type.getDescriptor(returnType));
				visitAnnotationMembers((Annotation)method.invoke(av2), av2);
				av2.visitEnd();
			} else if(returnType.isEnum()) {
				Enum<?> e = (Enum<?>) method.invoke(a);
				av.visitEnum(method.getName(), Type.getInternalName(returnType), e.name());
			} else if (returnType.isArray() && !returnType.getComponentType().isPrimitive()) {
				AnnotationVisitor av2 = av.visitArray(method.getName());
				Object[] values = (Object[]) method.invoke(a);
				if(returnType.getComponentType().isAnnotation()) {
					for(Object o : values) {
						visitAnnotationMembers((Annotation) o, av2);
					}
				} else if(returnType.getComponentType().isEnum()) {
					String enumType = Type.getInternalName(returnType.getComponentType());
					for(Object o : values) {
						av2.visitEnum(null, enumType, ((Enum<?>)o).name());
					}
				} else {
					for(Object o : values) {
						av2.visit(null, o);
					}
				}
				av2.visitEnd();
			} else {
				av.visit(method.getName(), method.invoke(a));
			}
		}
	}
	
	/**
	 * @return
	 */
	public static String getSimpleName(Integer rank, Long id) {
		long serviceRank = (rank.longValue() - (long) Integer.MAX_VALUE) * -1;
		long serviceId = id.longValue();
		
		String rankHex = Long.toHexString(serviceRank);
		if(rankHex.length() < 8) {
			rankHex = "00000000".substring(rankHex.length(), 8).concat(rankHex);
		}
		String idHex = Long.toHexString(serviceId);
		if(idHex.length() < 16) {
			idHex = "0000000000000000".substring(idHex.length(), 16).concat(idHex);
		}
		String simpleName = String.format("Extension_%s_%s", rankHex, idHex);
		return simpleName;
	}
	
}
