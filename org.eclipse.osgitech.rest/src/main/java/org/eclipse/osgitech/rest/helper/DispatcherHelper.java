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
package org.eclipse.osgitech.rest.helper;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.osgitech.rest.runtime.application.JerseyApplicationProvider;

/**
 * Helper class for the dispatcher
 * @author Mark Hoffmann
 * @since 20.03.2018
 */
public class DispatcherHelper {
	
	/**
	 * Returns a {@link Set} of applications with name default, sorted by their ranking
	 * @param applications the {@link Collection} of applications
	 * @return a {@link Set} of application or an empty {@link Set}
	 */
	public static Set<JerseyApplicationProvider> getDefaultApplications(Collection<JerseyApplicationProvider> applications) {
		if (applications == null) {
			return Collections.emptySet();
		}
		
		Set<JerseyApplicationProvider> resultSet = applications.stream()
				.filter(JerseyApplicationProvider::isDefault)
				.sorted()
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return resultSet;
	}

	/**
	 * Returns the highest ranked default application with name default
	 * @param applications the {@link Collection} of applications
	 * @return a {@link Optional} of application 
	 */
	public static Optional<JerseyApplicationProvider> getDefaultApplication(Collection<JerseyApplicationProvider> applications) {
		if (applications == null) {
			return Optional.empty();
		}
		return getDefaultApplications(applications).stream().findFirst();
	}
	
}
