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

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.glassfish.jersey.internal.util.ReflectionHelper;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;

/**
 * 
 * @author timothyjward
 * @since 9 May 2022
 */
public class ExtensionProxyTest {

	PublicClassLoader pcl = new PublicClassLoader();

	@Test
	public void testNameOrdering() {
		assertEquals(ExtensionProxyFactory.getSimpleName(0, 1L), ExtensionProxyFactory.getSimpleName(0, 1L));

		assertTrue(
				ExtensionProxyFactory.getSimpleName(1, 1L).compareTo(ExtensionProxyFactory.getSimpleName(0, 2L)) < 0);
		assertTrue(
				ExtensionProxyFactory.getSimpleName(0, 1L).compareTo(ExtensionProxyFactory.getSimpleName(-1, 2L)) < 0);
		assertTrue(
				ExtensionProxyFactory.getSimpleName(0, 1L).compareTo(ExtensionProxyFactory.getSimpleName(0, 2L)) < 0);
		assertTrue(
				ExtensionProxyFactory.getSimpleName(-1, 1L).compareTo(ExtensionProxyFactory.getSimpleName(0, 2L)) > 0);
		assertTrue(
				ExtensionProxyFactory.getSimpleName(0, 1L).compareTo(ExtensionProxyFactory.getSimpleName(1, 2L)) > 0);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExceptionMapper() throws Exception {

		ExceptionMapper<NullPointerException> em = new TestExceptionMapper();

		Class<?> proxyClazz = pcl.define("test.ExceptionMapper", em, singletonList(ExceptionMapper.class));

		assertTrue(ExceptionMapper.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ExceptionMapper.class, pt.getRawType());
		assertEquals(NullPointerException.class, pt.getActualTypeArguments()[0]);

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> em);

		assertEquals(418,
				((ExceptionMapper<NullPointerException>) instance).toResponse(new NullPointerException()).getStatus());

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testRawExceptionMapper() throws Exception {

		ExceptionMapper em = new RawExceptionMapper();

		Class<?> proxyClazz = pcl.define("test.RawExceptionMapper", em, singletonList(ExceptionMapper.class));

		assertTrue(ExceptionMapper.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertEquals(ExceptionMapper.class, genericInterfaces[0]);

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> em);

		assertEquals(814, ((ExceptionMapper) instance).toResponse(new OutOfMemoryError()).getStatus());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testParameterExceptionMapper() throws Exception {

		ExceptionMapper<?> em = new ParameterExceptionMapper<>();

		Class<?> proxyClazz = pcl.define("test.ExceptionMapper", em, singletonList(ExceptionMapper.class));

		assertTrue(ExceptionMapper.class.isAssignableFrom(proxyClazz));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(1, typeParameters.length);
		assertTrue(TypeVariable.class.isInstance(typeParameters[0]));
		assertEquals("T", typeParameters[0].toString());
		Type[] bounds = ((TypeVariable<?>) typeParameters[0]).getBounds();
		assertEquals(1, bounds.length);
		assertEquals(Throwable.class, bounds[0]);

		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ExceptionMapper.class, pt.getRawType());
		assertTrue(TypeVariable.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("T", pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> em);

		assertEquals(451, ((ExceptionMapper<Throwable>) instance).toResponse(new RuntimeException()).getStatus());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testChildReifiedExceptionMapper() throws Exception {

		ExceptionMapper<WebApplicationException> em = new ChildReifiedExceptionMapper();

		Class<?> proxyClazz = pcl.define("test.ExceptionMapper", em, singletonList(ExceptionMapper.class));

		assertTrue(ExceptionMapper.class.isAssignableFrom(proxyClazz));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(0, typeParameters.length);

		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ExceptionMapper.class, pt.getRawType());
		assertEquals(WebApplicationException.class, pt.getActualTypeArguments()[0]);

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> em);

		assertEquals(404,
				((ExceptionMapper<WebApplicationException>) instance).toResponse(new NotFoundException()).getStatus());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testParameterContext() throws Exception {

		ContextResolver<? extends CharSequence> cr = new ParameterContext<>();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(1, typeParameters.length);
		assertTrue(TypeVariable.class.isInstance(typeParameters[0]));
		assertEquals("T", typeParameters[0].toString());
		Type[] bounds = ((TypeVariable<?>) typeParameters[0]).getBounds();
		assertEquals(1, bounds.length);
		assertEquals(Object.class, bounds[0]);

		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(TypeVariable.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("T", pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals("", ((ContextResolver<? extends CharSequence>) instance).getContext(String.class));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExtendsParameterContext() throws Exception {

		ContextResolver<List<Integer>> cr = new ExtendsParameterContext<>();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(1, typeParameters.length);
		assertTrue(TypeVariable.class.isInstance(typeParameters[0]));
		assertEquals("R", typeParameters[0].toString());
		Type[] bounds = ((TypeVariable<?>) typeParameters[0]).getBounds();
		assertEquals(1, bounds.length);
		assertEquals(Number.class, bounds[0]);

		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(ParameterizedType.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("java.util.List<R>", pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals(Collections.emptyList(), ((ContextResolver<List<Integer>>) instance).getContext(String.class));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testChildExtendsParameterContext() throws Exception {

		ContextResolver<List<Double>> cr = new ChildExtendsParameterContext();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(0, typeParameters.length);

		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(ParameterizedType.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("java.util.List<java.lang.Double>", pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals(Collections.singletonList(42.0d),
				((ContextResolver<List<Double>>) instance).getContext(String.class));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testWildcardParameterContext() throws Exception {

		ContextResolver<List<? super Integer>> cr = new WildcardParameterContext();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));
		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(ParameterizedType.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("java.util.List<? super java.lang.Integer>", pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals(Collections.emptyList(), ((ContextResolver<List<Integer>>) instance).getContext(String.class));

	}

	@Test
	public void testRedirectsParameterContext() throws Exception {

		ContextResolver<?> cr = new RedirectsParameterContext<>();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(1, typeParameters.length);
		assertTrue(TypeVariable.class.isInstance(typeParameters[0]));
		assertEquals("R", typeParameters[0].toString());

		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(TypeVariable.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("R", pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals("", ((ContextResolver<?>) instance).getContext(String.class));

	}

	@Test
	public void testChildExtendsRedirectedParameterContext() throws Exception {

		ContextResolver<Double> cr = new ChildExtendsRedirectedParameterContext();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));
		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(0, typeParameters.length);

		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertEquals(Double.class, pt.getActualTypeArguments()[0]);

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals(42.0d, ((ContextResolver<?>) instance).getContext(String.class));

	}

	@Test
	public void testChildExtraExtendsRedirectedParameterContext() throws Exception {

		ContextResolver<List<Double>> cr = new ChildExtraExtendsRedirectedParameterContext();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));
		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(ParameterizedType.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("java.util.List<java.lang.Double>", pt.getActualTypeArguments()[0].getTypeName());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals(Collections.singletonList(17.0d), ((ContextResolver<?>) instance).getContext(String.class));

	}

	@Test
	public void testIndirectlyRedirectsParameterContext() throws Exception {

		ContextResolver<?> cr = new IndirectlyRedirectsParameterContext<>();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(1, typeParameters.length);
		assertTrue(TypeVariable.class.isInstance(typeParameters[0]));
		assertEquals("R", typeParameters[0].toString());

		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(ParameterizedType.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("java.util.List<R>", pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals("", ((ContextResolver<?>) instance).getContext(String.class));

	}

	@Test
	public void testChildExtraExtendsIndirectlyRedirectsParameterContext() throws Exception {

		ContextResolver<?> cr = new ChildExtraExtendsIndirectlyRedirectedParameterContext();

		Class<?> proxyClazz = pcl.define("test.ContextResolver", cr, singletonList(ContextResolver.class));

		TypeVariable<?>[] typeParameters = proxyClazz.getTypeParameters();
		assertEquals(0, typeParameters.length);

		assertTrue(ContextResolver.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ContextResolver.class, pt.getRawType());
		assertTrue(ParameterizedType.class.isInstance(pt.getActualTypeArguments()[0]));
		assertEquals("java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>",
				pt.getActualTypeArguments()[0].toString());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> cr);

		assertEquals(Collections.singletonList(Collections.singletonMap("foo", 42)),
				((ContextResolver<?>) instance).getContext(String.class));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAnnotatedExceptionMapper() throws Exception {

		ExceptionMapper<IllegalArgumentException> em = new AnnotatedExceptionMapper();

		Class<?> proxyClazz = pcl.define("test.AnnotatedExceptionMapper", em, singletonList(ExceptionMapper.class));

		assertTrue(ExceptionMapper.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(1, genericInterfaces.length);
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(ExceptionMapper.class, pt.getRawType());
		assertEquals(IllegalArgumentException.class, pt.getActualTypeArguments()[0]);

		assertEquals(1, proxyClazz.getAnnotations().length);
		assertNotNull(proxyClazz.getAnnotation(Path.class));

		Path path = proxyClazz.getAnnotation(Path.class);
		assertEquals("boo", path.value());

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> em);

		assertEquals(777, ((ExceptionMapper<IllegalArgumentException>) instance)
				.toResponse(new IllegalArgumentException()).getStatus());

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testMultiInterfaceMapper() throws Exception {

		TestMultiInterface mi = new TestMultiInterface();

		// Deliberately re-order the interfaces relative to the implements clause
		Class<?> proxyClazz = pcl.define("test.MultiInterface", mi,
				Arrays.asList(MessageBodyWriter.class, MessageBodyReader.class));

		assertTrue(MessageBodyReader.class.isAssignableFrom(proxyClazz));
		assertTrue(MessageBodyWriter.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();

		assertEquals(2, genericInterfaces.length);

		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(MessageBodyWriter.class, pt.getRawType());
		assertEquals(Long.class, pt.getActualTypeArguments()[0]);

		assertTrue(genericInterfaces[1] instanceof ParameterizedType);
		pt = (ParameterizedType) genericInterfaces[1];
		assertEquals(MessageBodyReader.class, pt.getRawType());
		assertEquals(Integer.class, pt.getActualTypeArguments()[0]);

		Object instance = proxyClazz.getConstructor(Supplier.class).newInstance((Supplier<?>) () -> mi);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		((MessageBodyWriter) instance).writeTo(42L, Long.class, null, null, null, null, baos);

		// 2 characters, "2a"
		assertArrayEquals(new byte[] { 0, 2, 50, 97 }, baos.toByteArray());

		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

		assertEquals(42, ((MessageBodyReader) instance).readFrom(Integer.class, null, null, null, null, bais));

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testMultiInterfaceGenericComplex() throws Exception {
		
		TestMultiInterfaceGenericComplex<Object, Object> mi = new TestMultiInterfaceGenericComplex<>();
		
		// Deliberately re-order the interfaces relative to the implements clause
		Class<?> proxyClazz = pcl.define("test.MultiInterfaceGenericComplex", mi, 
				Arrays.asList(MessageBodyWriter.class, MessageBodyReader.class));
		
		assertTrue(MessageBodyReader.class.isAssignableFrom(proxyClazz));
		assertTrue(MessageBodyWriter.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();
		
		assertEquals(2, genericInterfaces.length);
		
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(MessageBodyWriter.class, pt.getRawType());
		TypeVariable<?> tv = (TypeVariable) pt.getActualTypeArguments()[0];
		assertEquals("W", tv.getName());
		assertArrayEquals(new Type[] {Object.class}, tv.getBounds());
		
		assertTrue(genericInterfaces[1] instanceof ParameterizedType);
		pt = (ParameterizedType) genericInterfaces[1];
		assertEquals(MessageBodyReader.class, pt.getRawType());
		tv = (TypeVariable) pt.getActualTypeArguments()[0];
		assertEquals("R", tv.getName());
		assertArrayEquals(new Type[] {Object.class}, tv.getBounds());
		
		
		Object instance = proxyClazz.getConstructor(Supplier.class)
				.newInstance((Supplier<?>) () -> mi);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		((MessageBodyWriter) instance).writeTo("ignore me", Object.class, null, null, null, null, baos);
		
		assertArrayEquals(new byte[]{0x42}, baos.toByteArray());
		
		
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		
		
		assertEquals("tada", ((MessageBodyReader) instance).readFrom(Object.class, null, null, null, null, bais));
		
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testMultiInterfaceGenericComplexTwo() throws Exception {
		
		TestMultiInterfaceGenericComplexTwo<Integer, String> mi = new TestMultiInterfaceGenericComplexTwo<>();
		
		// Deliberately re-order the interfaces relative to the implements clause
		Class<?> proxyClazz = pcl.define("test.MultiInterfaceGenericComplexTwo", mi, 
				Arrays.asList(MessageBodyWriter.class, MessageBodyReader.class));
		
		assertTrue(MessageBodyReader.class.isAssignableFrom(proxyClazz));
		assertTrue(MessageBodyWriter.class.isAssignableFrom(proxyClazz));
		Type[] genericInterfaces = proxyClazz.getGenericInterfaces();
		
		assertEquals(2, genericInterfaces.length);
		
		assertTrue(genericInterfaces[0] instanceof ParameterizedType);
		ParameterizedType pt = (ParameterizedType) genericInterfaces[0];
		assertEquals(MessageBodyWriter.class, pt.getRawType());
		TypeVariable<?> tv = (TypeVariable) pt.getActualTypeArguments()[0];
		assertEquals("W", tv.getName());
		assertArrayEquals(new Type[] {CharSequence.class}, tv.getBounds());
		
		assertTrue(genericInterfaces[1] instanceof ParameterizedType);
		pt = (ParameterizedType) genericInterfaces[1];
		assertEquals(MessageBodyReader.class, pt.getRawType());
		tv = (TypeVariable) pt.getActualTypeArguments()[0];
		assertEquals("R", tv.getName());
		assertArrayEquals(new Type[] {Number.class}, tv.getBounds());
		
		Object instance = proxyClazz.getConstructor(Supplier.class)
				.newInstance((Supplier<?>) () -> mi);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		((MessageBodyWriter) instance).writeTo("banana", CharSequence.class, null, null, null, null, baos);
		
		// 4 characters, "anan" 
		assertArrayEquals(new byte[]{0,4,97,110,97,110}, baos.toByteArray());
		
		
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		
		
		assertEquals(17, ((MessageBodyReader) instance).readFrom(Number.class, null, null, null, null, bais));
		
	}

	@Test
	public void testMultiInterfaceMapperJerseyStyle() throws Exception {

		TestMultiInterfaceGenericComplexConcret mi = new TestMultiInterfaceGenericComplexConcret();

		ClassLoader classLoader = new ClassLoader(getClass().getClassLoader()) {

			/*
			 * (non-Javadoc)
			 * 
			 * @see java.lang.ClassLoader#findClass(java.lang.String)
			 */
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] b = ExtensionProxyFactory.generateClass("test.proxy.MultiExtenstion", mi,
						List.of(MessageBodyReader.class, MessageBodyWriter.class));
				return defineClass(name, b, 0, b.length, mi.getClass().getProtectionDomain());
			}
		};

		Class<?> class1 = classLoader.loadClass("test.proxy.MultiExtenstion");
		assertNotNull(class1);

		ReflectionHelper.DeclaringClassInterfacePair pair1 = ReflectionHelper.getClass(class1, MessageBodyWriter.class);
		assertNotNull(pair1);

		ReflectionHelper.DeclaringClassInterfacePair pair2 = ReflectionHelper.getClass(class1, MessageBodyReader.class);
		assertNotNull(pair2);
	}

	@Test
	public void testMultiInterfaceMapperJerseyStyleGeneric() throws Exception {

		TestMultiInterfaceGenericComplex<String, String> mi = new TestMultiInterfaceGenericComplex<String, String>();

		// Test Plain

		ReflectionHelper.DeclaringClassInterfacePair pair1 = ReflectionHelper.getClass(mi.getClass(),
				MessageBodyWriter.class);
		assertNotNull(pair1);

		ReflectionHelper.DeclaringClassInterfacePair pair2 = ReflectionHelper.getClass(mi.getClass(),
				MessageBodyReader.class);
		assertNotNull(pair2);

		ClassLoader classLoader = new ClassLoader(getClass().getClassLoader()) {

			/*
			 * (non-Javadoc)
			 * 
			 * @see java.lang.ClassLoader#findClass(java.lang.String)
			 */
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] b = ExtensionProxyFactory.generateClass("test.proxy.MultiExtenstion", mi,
						List.of(MessageBodyReader.class, MessageBodyWriter.class));
				return defineClass(name, b, 0, b.length, mi.getClass().getProtectionDomain());
			}
		};

		Class<?> class1 = classLoader.loadClass("test.proxy.MultiExtenstion");
		assertNotNull(class1);

		final Type[] gis = class1.getGenericInterfaces();

		assertEquals(2, gis.length);

		pair1 = ReflectionHelper.getClass(class1, MessageBodyWriter.class);
		assertNotNull(pair1);

		pair2 = ReflectionHelper.getClass(class1, MessageBodyReader.class);
		assertNotNull(pair2);
	}

	public static class TestExceptionMapper implements ExceptionMapper<NullPointerException> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
		 */
		@Override
		public Response toResponse(NullPointerException exception) {
			// I'm a teapot
			return Response.status(418).build();
		}

	}

	@SuppressWarnings("rawtypes")
	public static class RawExceptionMapper implements ExceptionMapper {

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
		 */
		@Override
		public Response toResponse(Throwable exception) {
			// What's an 8xx response?!?
			return Response.status(814).build();
		}

	}

	public static class ParameterExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
		 */
		@Override
		public Response toResponse(T exception) {
			// Unavailable for legal reasons
			return Response.status(451).build();
		}

	}

	public static class ChildReifiedExceptionMapper extends ParameterExceptionMapper<WebApplicationException> {

		@Override
		public Response toResponse(WebApplicationException exception) {
			return Response.status(exception.getResponse().getStatus()).build();
		}

	}

	public static class ParameterContext<T> implements ContextResolver<T> {

		@SuppressWarnings("unchecked")
		@Override
		public T getContext(Class<?> type) {
			try {
				return (T) type.getConstructor().newInstance();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

	}

	public static class ExtendsParameterContext<R extends Number> implements ContextResolver<List<R>> {

		@Override
		public List<R> getContext(Class<?> type) {
			return new ArrayList<>();
		}

	}

	public static class ChildExtendsParameterContext extends ExtendsParameterContext<Double> {

		@Override
		public List<Double> getContext(Class<?> type) {
			return Collections.singletonList(42.0d);
		}

	}

	public static class WildcardParameterContext implements ContextResolver<List<? super Integer>> {

		@Override
		public List<? super Integer> getContext(Class<?> type) {
			return new ArrayList<>();
		}

	}

	public static class RedirectsParameterContext<R> extends ParameterContext<R> {

	}

	public static class ChildExtendsRedirectedParameterContext extends RedirectsParameterContext<Double> {

		@Override
		public Double getContext(Class<?> type) {
			return 42.0d;
		}

	}

	public static class ChildExtraExtendsRedirectedParameterContext extends RedirectsParameterContext<List<Double>> {

		@Override
		public List<Double> getContext(Class<?> type) {
			return Collections.singletonList(17.0d);
		}

	}

	public static class IndirectlyRedirectsParameterContext<R> extends ParameterContext<List<R>> {

	}

	public static class ChildExtraExtendsIndirectlyRedirectedParameterContext
			extends IndirectlyRedirectsParameterContext<Map<String, Integer>> {

		@Override
		public List<Map<String, Integer>> getContext(Class<?> type) {
			return Collections.singletonList(Collections.singletonMap("foo", 42));
		}

	}

	@Path("boo")
	public static class AnnotatedExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
		 */
		@Override
		public Response toResponse(IllegalArgumentException exception) {
			// All lucky 7s
			return Response.status(777).build();
		}

	}

	public static class TestMultiInterface implements MessageBodyReader<Integer>, MessageBodyWriter<Long> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyWriter#isWriteable(java.lang.Class,
		 * java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType)
		 */
		@Override
		public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyWriter#writeTo(java.lang.Object,
		 * java.lang.Class, java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType, jakarta.ws.rs.core.MultivaluedMap,
		 * java.io.OutputStream)
		 */
		@Override
		public void writeTo(Long t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
				throws IOException, WebApplicationException {
			try (DataOutputStream daos = new DataOutputStream(entityStream)) {
				daos.writeUTF(Long.toHexString(t));
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyReader#isReadable(java.lang.Class,
		 * java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType)
		 */
		@Override
		public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyReader#readFrom(java.lang.Class,
		 * java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType, jakarta.ws.rs.core.MultivaluedMap,
		 * java.io.InputStream)
		 */
		@Override
		public Integer readFrom(Class<Integer> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
				throws IOException, WebApplicationException {
			try (DataInputStream dis = new DataInputStream(entityStream)) {
				return Integer.valueOf(dis.readUTF(), 16);
			}
		}
	}

	public static abstract class BaseTestMultiInterfaceGenericCompelex<R, W>
			implements MessageBodyReader<R>, MessageBodyWriter<W> {

	}

	public static class TestMultiInterfaceGenericComplex<R extends Object, W extends Object>
			extends BaseTestMultiInterfaceGenericCompelex<R, W> {

		@Override
		public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		@Override
		public void writeTo(W t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
				throws IOException, WebApplicationException {
			entityStream.write(0x42);
		}

		@Override
		public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		@SuppressWarnings("unchecked")
		@Override
		public R readFrom(Class<R> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
				throws IOException, WebApplicationException {
			return (R) "tada";
		}
	}

	public static class TestMultiInterfaceGenericComplexTwo<R extends Number, W extends CharSequence>
			extends BaseTestMultiInterfaceGenericCompelex<R, W> {

		@Override
		public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		@Override
		public void writeTo(W t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
				throws IOException, WebApplicationException {
			try (DataOutputStream daos = new DataOutputStream(entityStream)) {
				daos.writeUTF(t.subSequence(1, t.length() - 1).toString());
			}
		}

		@Override
		public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		@SuppressWarnings("unchecked")
		@Override
		public R readFrom(Class<R> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
				throws IOException, WebApplicationException {
			return (R) Integer.valueOf(17);
		}
	}

	public static class TestMultiInterfaceGenericComplexConcret
			extends BaseTestMultiInterfaceGenericCompelex<String, String> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyWriter#isWriteable(java.lang.Class,
		 * java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType)
		 */
		@Override
		public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyWriter#writeTo(java.lang.Object,
		 * java.lang.Class, java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType, jakarta.ws.rs.core.MultivaluedMap,
		 * java.io.OutputStream)
		 */
		@Override
		public void writeTo(String t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
				throws IOException, WebApplicationException {

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyReader#isReadable(java.lang.Class,
		 * java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType)
		 */
		@Override
		public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see jakarta.ws.rs.ext.MessageBodyReader#readFrom(java.lang.Class,
		 * java.lang.reflect.Type, java.lang.annotation.Annotation[],
		 * jakarta.ws.rs.core.MediaType, jakarta.ws.rs.core.MultivaluedMap,
		 * java.io.InputStream)
		 */
		@Override
		public String readFrom(Class<String> type, Type genericType, Annotation[] annotations, MediaType mediaType,
				MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
				throws IOException, WebApplicationException {
			return "tada";
		}
	}

	public static class PublicClassLoader extends ClassLoader {

		public Class<?> define(String name, Object delegate, List<Class<?>> contracts) {
			byte[] b = ExtensionProxyFactory.generateClass(name, delegate, contracts);
			return defineClass(name, b, 0, b.length);
		}
	}
}
