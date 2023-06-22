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
package org.eclipse.osgitech.rest;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * 
 * @author mark
 * @since 05.07.2022
 */
public class ExtensionComparatorTest {
	
	static class Element {
		public Integer rank;
		public Long id;
	}
	
	Comparator<Element> elementComparator = new Comparator<ExtensionComparatorTest.Element>() {
		/* 
		 * (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(Element o1, Element o2) {
			if (o1.rank.equals(o2.rank)) {
				return o1.id.compareTo(o2.id);
			}
			return o2.rank.compareTo(o1.rank);
		}
	};
	
	/**
	 * lower service id over higher service id
	 * this means lower service ids are longer living services
	 */
	@Test
	public void testComparator01() {
		Element e1 = new Element();
		e1.id = 1l;
		e1.rank = 0;
		
		Element e2 = new Element();
		e2.id = 2l;
		e2.rank = 0;
		
		List<Element> elements = new ArrayList<ExtensionComparatorTest.Element>();
		elements.add(e2);
		elements.add(e1);
		
		List<Element> collect = elements.stream().sorted(elementComparator).collect(Collectors.toList());
		assertEquals(e1, collect.get(0));
		assertEquals(e2, collect.get(1));
	}
	/**
	 * lower service id over higher service id
	 * this means lower service ids are longer living services
	 */
	@Test
	public void testComparator02() {
		Element e1 = new Element();
		e1.id = 1l;
		e1.rank = 0;
		
		Element e2 = new Element();
		e2.id = 2l;
		e2.rank = 10;
		
		List<Element> elements = new ArrayList<ExtensionComparatorTest.Element>();
		elements.add(e1);
		elements.add(e2);
		
		List<Element> collect = elements.stream().sorted(elementComparator).collect(Collectors.toList());
		assertEquals(e2, collect.get(0));
		assertEquals(e1, collect.get(1));
	}

}
