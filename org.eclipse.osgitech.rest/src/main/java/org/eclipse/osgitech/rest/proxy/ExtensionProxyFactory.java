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
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
		Class<? extends Object> delegateClazz = delegate.getClass();
		Map<String, ParameterizedType> typeInfo = getInterfacesAndGenericSuperclasses(delegateClazz, new ArrayList<>(), new HashSet<>(contracts))
				.stream().collect(Collectors.toMap(i -> i.getRawType().getTypeName(), Function.identity()));
		
		String sig = generateGenericClassSignature(delegateClazz, typeInfo, contracts);
		
		
		try {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			
			String internalName = className.replace('.', '/');
			cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, 
					sig, OBJECT_INTERNAL_NAME, 
					contracts.stream()
						.map(Type::getInternalName)
						.toArray(String[]::new));
			
			for (Annotation annotation : delegateClazz.getAnnotations()) {
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

	private static List<ParameterizedType> getInterfacesAndGenericSuperclasses(Class<?> toCheck, List<ParameterizedType> fromChildren, Set<Class<?>> remainingContracts) {
		if(toCheck == Object.class) {
			return fromChildren;
		}
		
		for (java.lang.reflect.Type type : toCheck.getGenericInterfaces()) {
			if(type instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) type;
				if(remainingContracts.remove(pt.getRawType())) {
					fromChildren.add(pt);
				}
			}
		}
		
		java.lang.reflect.Type genericSuperclass = toCheck.getGenericSuperclass();
		if(genericSuperclass instanceof ParameterizedType) {
			fromChildren.add((ParameterizedType) genericSuperclass);
		}
		
		return getInterfacesAndGenericSuperclasses(toCheck.getSuperclass(), fromChildren, remainingContracts);
	}
	
	private static Predicate<TypeVariable<?>> isUsedInTypeInfo(Map<String, ParameterizedType> typeInfo) {
		return tv -> typeInfo.values().stream()
				.flatMap(ExtensionProxyFactory::toTypeVariables)
				.anyMatch(t -> tv.getName().equals(t.getName()));
	}
	
	/**
	 * @param typeInfo
	 * @return
	 */
	private static String generateGenericClassSignature(Class<?> delegateClazz, Map<String, ParameterizedType> typeInfo, List<Class<?>> contracts) {
		if(typeInfo.isEmpty()) {
			return null;
		}
		
		SignatureWriter writer = new SignatureWriter();
		
		// Handle class
		Arrays.stream(delegateClazz.getTypeParameters())
			.filter(isUsedInTypeInfo(typeInfo))
			.forEach(tv -> {
				writer.visitFormalTypeParameter(tv.getName());
				SignatureVisitor cb = writer.visitClassBound();
				Arrays.stream(tv.getBounds()).forEach(b -> visitTypeParameter(b, cb, delegateClazz, typeInfo));
				cb.visitEnd();
			});
		
		// Generated class extends Object
		SignatureVisitor sv = writer.visitSuperclass();
		sv.visitClassType(OBJECT_INTERNAL_NAME);
		sv.visitEnd();
		
		// Handle interfaces
		for(Class<?> contract : contracts) {
			if(typeInfo.containsKey(contract.getName())) {
				Class<?> directDeclarer = delegateClazz;
				check: for(;;) {
					for(Class<?> iface : directDeclarer.getInterfaces()) {
						if(iface == contract) {
							break check;
						}
					}
					directDeclarer = directDeclarer.getSuperclass();
					if(directDeclarer == Object.class) {
						throw new IllegalArgumentException("The contract " + contract + " is not implemented in the hierarchy");
					}
				}
				SignatureVisitor iv = writer.visitInterface();
				iv.visitClassType(Type.getInternalName(contract));
				for(java.lang.reflect.Type t : typeInfo.get(contract.getName()).getActualTypeArguments()) {
					if(TypeVariable.class.isInstance(t)) {
						visitTypeParameter(t, iv, directDeclarer, typeInfo);
					} else {
						SignatureVisitor tav = iv.visitTypeArgument(SignatureVisitor.INSTANCEOF);
						visitTypeParameter(t, tav, directDeclarer, typeInfo);
						tav.visitEnd();
					}
				}
				iv.visitEnd();
			}
		}
		 
		writer.visitEnd();
		return writer.toString();
	}
	
	private static java.lang.reflect.Type getPossibleReifiedTypeFor(TypeVariable<?> tv, Class<?> directDeclarer, Map<String, ParameterizedType> typeInfo) {
		ParameterizedType pt = typeInfo.get(directDeclarer.getName());
		if(pt != null) {
			TypeVariable<?>[] decParams = directDeclarer.getTypeParameters();
			for (int i = 0; i < decParams.length; i++) {
				TypeVariable<?> decTv = decParams[i];
				if(decTv.getName().equals(tv.getName())) {
					return pt.getActualTypeArguments()[i];
				}
			}
		} 
		return tv;
	}
	
	private static Stream<TypeVariable<?>> toTypeVariables(java.lang.reflect.Type t) {
		if(t instanceof Class<?>) {
			return Stream.empty();
		} else if (t instanceof TypeVariable<?>) {
			return Stream.of((TypeVariable<?>)t);
		} else if (t instanceof ParameterizedType) {
			return Arrays.stream(((ParameterizedType) t).getActualTypeArguments())
					.flatMap(ExtensionProxyFactory::toTypeVariables);
		} else {
			throw new IllegalArgumentException("Unkown type " + t.getClass());
		}
	}

	private static void visitTypeParameter(java.lang.reflect.Type t, SignatureVisitor sv, Class<?> directDeclarer, Map<String, ParameterizedType> typeInfo) {
		if(t instanceof Class<?>) {
			Class<?> clazz = (Class<?>) t;
			if(clazz.isPrimitive()) {
				sv.visitBaseType(Type.getDescriptor(clazz).charAt(0));
			} else if (clazz.isArray()) {
				SignatureVisitor av = sv.visitArrayType();
				visitTypeParameter(clazz.getComponentType(), av, directDeclarer, typeInfo);
				av.visitEnd();
			} else {
				sv.visitClassType(Type.getInternalName(clazz));
			}
		} else if (t instanceof ParameterizedType){
			ParameterizedType pt = (ParameterizedType) t;
			sv.visitClassType(Type.getInternalName((Class<?>)pt.getRawType()));
			Arrays.stream(pt.getActualTypeArguments()).forEach(ta -> visitTypeParameter(ta, sv, directDeclarer, typeInfo));
		} else if (t instanceof TypeVariable<?>) {
			TypeVariable<?> tv = (TypeVariable<?>) t;
			t = getPossibleReifiedTypeFor((TypeVariable<?>)t, directDeclarer, typeInfo);
			if(t == tv) {
				sv.visitTypeArgument(SignatureVisitor.INSTANCEOF).visitTypeVariable(tv.getName());
			} else {
				SignatureVisitor tav = sv.visitTypeArgument(SignatureVisitor.INSTANCEOF);
				visitTypeParameter(t, tav, directDeclarer, typeInfo);
				tav.visitEnd();
			}
		} else if (t instanceof WildcardType) {
			WildcardType wt = (WildcardType) t;
			SignatureVisitor tav;
			java.lang.reflect.Type[] types;
			if(wt.getLowerBounds().length > 0) {
				tav = sv.visitTypeArgument(SignatureVisitor.SUPER);
				types = wt.getLowerBounds();
			} else {
				tav = sv.visitTypeArgument(SignatureVisitor.EXTENDS);
				types = wt.getUpperBounds();
			}
			Arrays.stream(types).forEach(ty -> visitTypeParameter(ty, tav, directDeclarer, typeInfo));
			tav.visitEnd();
		} else {
			throw new IllegalArgumentException("Unhandled generic type " + t.getClass() + " " + t.toString());
 		}
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
