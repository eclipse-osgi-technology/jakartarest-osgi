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
package org.eclipse.osgitech.rest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.osgitech.rest.util.OptionalResponseFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.container.ContainerResponseContext;

/**
 * Tests the {@link OptionalResponseFilter}
 * @author Mark Hoffmann
 * @since 07.11.2022
 */
@ExtendWith(MockitoExtension.class)
public class OptionalResponseFilterTest {
	
	@Mock
	private ContainerResponseContext responseCtx;
	@Captor 
	ArgumentCaptor<Object> entityCaptor;
	@Captor 
	ArgumentCaptor<Integer> httpResponseCaptor;
	
	@Test
	public void testNoOptionalString() {
		Mockito.lenient().when(responseCtx.getEntity()).thenReturn("Test");
		OptionalResponseFilter orf = new OptionalResponseFilter();
		try {
			orf.filter(null, responseCtx);
		} catch (IOException e) {
			fail("Filter has thrown an unexpected eception");
		}
		verify(responseCtx, never()).setEntity(any());
		verify(responseCtx, never()).setStatus(anyInt());
	}
	
	@Test
	public void testNoOptionalNull() {
		Mockito.lenient().when(responseCtx.getEntity()).thenReturn(null);
		OptionalResponseFilter orf = new OptionalResponseFilter();
		try {
			orf.filter(null, responseCtx);
		} catch (IOException e) {
			fail("Filter has thrown an unexpected eception");
		}
		verify(responseCtx, never()).setEntity(any());
		verify(responseCtx, never()).setStatus(anyInt());
	}
	
	@Test
	public void testEmptyOptional() {
		Mockito.lenient().when(responseCtx.getEntity()).thenReturn(Optional.empty());
		OptionalResponseFilter orf = new OptionalResponseFilter();
		try {
			orf.filter(null, responseCtx);
		} catch (IOException e) {
			fail("Filter has thrown an unexpected eception");
		}
		verify(responseCtx).setEntity(entityCaptor.capture());
		assertNull(entityCaptor.getValue());
		verify(responseCtx).setStatus(httpResponseCaptor.capture());
		assertEquals(204, httpResponseCaptor.getValue());
	}
	
	@Test
	public void testOptionalOfNullable() {
		Mockito.lenient().when(responseCtx.getEntity()).thenReturn(Optional.ofNullable(null));
		OptionalResponseFilter orf = new OptionalResponseFilter();
		try {
			orf.filter(null, responseCtx);
		} catch (IOException e) {
			fail("Filter has thrown an unexpected eception");
		}
		verify(responseCtx).setEntity(entityCaptor.capture());
		assertNull(entityCaptor.getValue());
		verify(responseCtx).setStatus(httpResponseCaptor.capture());
		assertEquals(204, httpResponseCaptor.getValue());
	}
	
	@Test
	public void testOptionalOfNotNull() {
		Mockito.lenient().when(responseCtx.getEntity()).thenReturn(Optional.of("Test"));
		OptionalResponseFilter orf = new OptionalResponseFilter();
		try {
			orf.filter(null, responseCtx);
		} catch (IOException e) {
			fail("Filter has thrown an unexpected eception");
		}
		verify(responseCtx).setEntity(entityCaptor.capture());
		assertEquals("Test", entityCaptor.getValue());
		verify(responseCtx, never()).setStatus(anyInt());
	}

}
