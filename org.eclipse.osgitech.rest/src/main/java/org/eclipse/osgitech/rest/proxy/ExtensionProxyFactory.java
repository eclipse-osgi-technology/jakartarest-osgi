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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
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
	 * Generate a proxy class which copies the signature of the delegate
	 * 
	 * @param className - the name to use for the new class
	 * @param delegate - the object to proxy
	 * @param contracts - the extension contracts to honour 
	 */
	public static byte[] generateClass(String className, Object delegate, List<Class<?>> contracts) {
		Class<? extends Object> delegateClazz = delegate.getClass();
		Map<String, ParameterizedType> typeInfo = new HashMap<>();
		Map<String, String> contextMapping = new HashMap<>();
				
		populateInterfacesAndGenericSuperclasses(delegateClazz, typeInfo, contextMapping, new HashSet<>(contracts));
		
		String sig = generateGenericClassSignature(delegateClazz, typeInfo, contextMapping, contracts);
		
		
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

	/**
	 * Gather information about the type variables and generic superclasses for
	 * the supplied class
	 * 
	 * @param toCheck - the class to check
	 * @param typeInfo - the known type name to type information mapping (modified by this method)
	 * @param contextMapping - A mapping of type names to the class which defines them (modified by this method)
	 * @param remainingContracts - the extension contracts left to be checked
	 */
	private static void populateInterfacesAndGenericSuperclasses(Class<? extends Object> toCheck,
			Map<String, ParameterizedType> typeInfo, Map<String, String> contextMapping, Set<Class<?>> remainingContracts) {
		if(toCheck == Object.class) {
			return;
		}
		
		for (java.lang.reflect.Type type : toCheck.getGenericInterfaces()) {
			if(type instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) type;
				if(remainingContracts.remove(pt.getRawType())) {
					String name = ((Class<?>)pt.getRawType()).getName();
					typeInfo.put(name, pt);
					contextMapping.put(name, toCheck.getName());
				}
			}
		}
		
		java.lang.reflect.Type genericSuperclass = toCheck.getGenericSuperclass();
		if(genericSuperclass instanceof ParameterizedType) {
			String name = toCheck.getSuperclass().getName();
			typeInfo.put(name, (ParameterizedType) genericSuperclass);
			contextMapping.put(toCheck.getName(), name);
		}
		
		populateInterfacesAndGenericSuperclasses(toCheck.getSuperclass(), typeInfo, contextMapping, remainingContracts);
		return;
	}
	
	/**
	 * Determine whether the given Type Variable is used (possibly indirectly) in
	 * one or more contract interfaces
	 * 
	 * @param tv - the type variable to check
	 * @param typeInfo - the known type name to type information mapping
	 * @param context - A mapping of type names to the class which defines them
	 * @param contracts - the interfaces that we're proxying
	 * @return true if <code>tv</code> is used in the contracts
	 */
	private static <T> boolean isUsedInContracts(TypeVariable<Class<T>> tv, Map<String, ParameterizedType> typeInfo, Map<String, String> context, List<Class<?>> contracts) {
		BiPredicate<String, String> contractUses = (varName, contextClass) -> contracts.stream()
				.filter(c -> contextClass.equals(context.get(c.getName())))
				.map(c -> typeInfo.get(c.getName()))
				.flatMap(ExtensionProxyFactory::toTypeVariables)
				.anyMatch(t -> varName.equals(t.getName()));

		String decClassName = tv.getGenericDeclaration().getName();
		String variableName = tv.getName();
		
		if(contractUses.test(variableName, decClassName)) {
			return true;
		}
		// Check the super class next
		ParameterizedType superType = typeInfo.get(context.get(decClassName));
		if(superType == null) {
			return false;
		}
		// Are any of the generic types of the super class linked to our type variable
		// *and* then used in the contracts? Remember to check recursively up the hierarchy
		java.lang.reflect.Type[] superTypeArguments = superType.getActualTypeArguments();
		for (int i = 0; i < superTypeArguments.length; i++) {
			if(ExtensionProxyFactory.toTypeVariables(superTypeArguments[i])
					.anyMatch(t -> variableName.equals(t.getName()))) {
				Class<?> raw = (Class<?>) superType.getRawType();
				if(isUsedInContracts(raw.getTypeParameters()[i], typeInfo, context, contracts)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Generate the Generic class signature for this proxy class.
	 * <p>
	 * The syntax is available at <a 
	 * href="https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.7.9.1"/>
	 * but we use ASM's generator to help.
	 * 
	 * @param delegateClazz - the class that we're creating a proxy for
	 * @param typeInfo - the known type name to type information mapping
	 * @param context - A mapping of type names to the class which defines them
	 * @param contracts - the interfaces that we're proxying
	 * @return the class signature
	 */
	private static String generateGenericClassSignature(Class<?> delegateClazz, Map<String, ParameterizedType> typeInfo, Map<String, String> context, List<Class<?>> contracts) {
		if(typeInfo.isEmpty()) {
			return null;
		}
		
		SignatureWriter writer = new SignatureWriter();
		
		// Handle class
		Arrays.stream(delegateClazz.getTypeParameters())
			.filter(tv -> isUsedInContracts(tv, typeInfo, context, contracts))
			.forEach(tv -> {
				writer.visitFormalTypeParameter(tv.getName());
				SignatureVisitor cb = writer.visitClassBound();
				Arrays.stream(tv.getBounds()).forEach(b -> visitTypeParameter(b, cb, typeInfo, context));
				cb.visitEnd();
			});
		
		// Generated class extends Object
		SignatureVisitor sv = writer.visitSuperclass();
		sv.visitClassType(OBJECT_INTERNAL_NAME);
		sv.visitEnd();
		
		// Handle interfaces
		for(Class<?> contract : contracts) {
			if(typeInfo.containsKey(contract.getName())) {
				SignatureVisitor iv = writer.visitInterface();
				iv.visitClassType(Type.getInternalName(contract));
				for(java.lang.reflect.Type t : typeInfo.get(contract.getName()).getActualTypeArguments()) {
					if(TypeVariable.class.isInstance(t)) {
						visitTypeParameter(t, iv, typeInfo, context);
					} else {
						SignatureVisitor tav = iv.visitTypeArgument(SignatureVisitor.INSTANCEOF);
						visitTypeParameter(t, tav, typeInfo, context);
						tav.visitEnd();
					}
				}
				iv.visitEnd();
			}
		}
		 
		writer.visitEnd();
		return writer.toString();
	}
	
	/**
	 * Find the reified type information, if any, for the supplied type variable
	 * 
	 * @param t - the type to reify
	 * @param typeInfo - the known type name to type information mapping
	 * @param context - A mapping of type names to the class which defines them
	 */
	private static java.lang.reflect.Type getPossibleReifiedTypeFor(TypeVariable<?> tv, Map<String, ParameterizedType> typeInfo, Map<String, String> context) {
		Class<?> declaringType = (Class<?>) tv.getGenericDeclaration();
		
		ParameterizedType pt = typeInfo.get(declaringType.getName());
		if(pt != null) {
			TypeVariable<?>[] decParams = declaringType.getTypeParameters();
			for (int i = 0; i < decParams.length; i++) {
				TypeVariable<?> decTv = decParams[i];
				if(decTv.getName().equals(tv.getName())) {
					java.lang.reflect.Type type = pt.getActualTypeArguments()[i];
					if(type instanceof TypeVariable<?>) {
						return getPossibleReifiedTypeFor((TypeVariable<?>) type, typeInfo, context);
					} else {
						return type;
					}
				}
			}
		} 
		return tv;
	}
	
	/**
	 * Maps a type variable to a stream of nested variables
	 * @param t the variable to map
	 * @return
	 */
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

	/**
	 * Fill in the signature for the supplied type variable
	 * <p>
	 * Note that we don't visit the end of any type parameter that we create. This is
	 * expected to be handled by the caller closing their SignatureVisitor
	 * 
	 * @param t - the type to visit
	 * @param sv - the visitor to update with type information
	 * @param typeInfo - the known type name to type information mapping
	 * @param context - A mapping of type names to the class which defines them
	 */
	private static void visitTypeParameter(java.lang.reflect.Type t, SignatureVisitor sv, Map<String, ParameterizedType> typeInfo, Map<String, String> context) {
		if(t instanceof Class<?>) {
			Class<?> clazz = (Class<?>) t;
			if(clazz.isPrimitive()) {
				sv.visitBaseType(Type.getDescriptor(clazz).charAt(0));
			} else if (clazz.isArray()) {
				SignatureVisitor av = sv.visitArrayType();
				visitTypeParameter(clazz.getComponentType(), av, typeInfo, context);
				// Do not visit the end
			} else {
				sv.visitClassType(Type.getInternalName(clazz));
			}
		} else if (t instanceof ParameterizedType){
			ParameterizedType pt = (ParameterizedType) t;
			sv.visitClassType(Type.getInternalName((Class<?>)pt.getRawType()));
			Arrays.stream(pt.getActualTypeArguments()).forEach(ta -> {
				SignatureVisitor tav = sv.visitTypeArgument(SignatureVisitor.INSTANCEOF);
				visitTypeParameter(ta, tav, typeInfo, context);
				// Here we must visit the end as we created a new class type context
				tav.visitEnd();
			});
		} else if (t instanceof TypeVariable<?>) {
			TypeVariable<?> tv = (TypeVariable<?>) t;
			t = getPossibleReifiedTypeFor((TypeVariable<?>)t, typeInfo, context);
			if(t == tv) {
				sv.visitTypeArgument(SignatureVisitor.INSTANCEOF).visitTypeVariable(tv.getName());
			} else {
				SignatureVisitor tav = sv.visitTypeArgument(SignatureVisitor.INSTANCEOF);
				visitTypeParameter(t, tav, typeInfo, context);
				// Do not visit the end
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
			Arrays.stream(types).forEach(ty -> visitTypeParameter(ty, tav, typeInfo, context));
			// Do not visit the end
		} else {
			throw new IllegalArgumentException("Unhandled generic type " + t.getClass() + " " + t.toString());
 		}
	}
	
	/**
	 * Visit the member information of a defined annotation and write it into the
	 * class file.
	 * 
	 * @param a - the annotation to copy from
	 * @param av - the visitor to copy into
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
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
	 * Get the simple name for this generated class
	 * 
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
