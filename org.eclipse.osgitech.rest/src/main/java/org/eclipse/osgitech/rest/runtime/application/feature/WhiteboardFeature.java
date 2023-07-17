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
package org.eclipse.osgitech.rest.runtime.application.feature;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.osgitech.rest.runtime.application.JerseyExtensionProvider;
import org.eclipse.osgitech.rest.runtime.application.JerseyExtensionProvider.JerseyExtension;

import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

/**
 * A {@link Feature} implementation registering all extensions as singleton and according to there provided contracts. 
 * @author Juergen Albert
 * @since 16.01.2018
 */
public class WhiteboardFeature implements Feature{

	public static Comparator<Map.Entry<String, JerseyExtensionProvider>> PROVIDER_COMPARATOR = (e1, e2) -> 
		e1.getValue().compareTo(e2.getValue());

	Map<String, JerseyExtensionProvider> extensions;

	Map<JerseyExtensionProvider, JerseyExtension> extensionInstanceTrackingMap = new HashMap<>();


	public WhiteboardFeature(Map<String, JerseyExtensionProvider> extensions) {
		this.extensions = extensions.entrySet().stream().sorted(PROVIDER_COMPARATOR).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue)->oldValue, LinkedHashMap::new));
	}

	/* (non-Javadoc)
	 * @see jakarta.ws.rs.core.Feature#configure(jakarta.ws.rs.core.FeatureContext)
	 */
	@Override
	public boolean configure(FeatureContext context) {
		AtomicInteger priority = new AtomicInteger(Priorities.USER + 1000);
		extensions.forEach((k, extension) -> {

			JerseyExtension je = extension.getExtension(context);

			if(je != null) {
				extensionInstanceTrackingMap.put(extension, je);
				Map<Class<?>,Integer> contractPriorities = je.getContractPriorities();
				if (contractPriorities.isEmpty()) {
					context.register(je.getExtensionObject(), priority.getAndIncrement());
				} else {
					context.register(je.getExtensionObject(), je.getContractPriorities());
				}
			}
		});
		return true;
	}

	public void dispose() {
		extensionInstanceTrackingMap.forEach((k,v) -> {
			try {
				v.dispose();
			} catch (IllegalArgumentException e) {
				// we can ignore this. Will be thrown by felix if it 
			}
		});
		extensionInstanceTrackingMap.clear();
		extensions.clear();
	}
}
