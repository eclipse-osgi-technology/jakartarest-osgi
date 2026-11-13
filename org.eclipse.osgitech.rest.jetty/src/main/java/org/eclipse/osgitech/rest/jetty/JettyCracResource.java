/**
 * Copyright (c) 2024 Dirk Fauth and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Dirk Fauth - initial API and implementation
 */
package org.eclipse.osgitech.rest.jetty;

import java.util.Arrays;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * This class is used to add CRaC support to the jetty bundle by using the
 * <code>org.crac</code> API. It adapts the examples and best practices from the
 * CRaC documentation and the examples.
 * 
 * @see <a href="https://github.com/CRaC/org.crac">`org.crac` API</a>
 * @see <a href=
 *      "https://docs.azul.com/core/crac/crac-tips-tricks#implementing-resource-as-inner-class">Implementing
 *      Resource as Inner Class</a>
 * @see <a href=
 *      "https://github.com/CRaC/docs/blob/master/STEP-BY-STEP.md">Step-by-step
 *      CRaC support for a Jetty app</a>
 * @see <a href=
 *      "https://github.com/CRaC/example-jetty/blob/master/src/main/java/com/example/App.java">CRaC
 *      example-jetty</a>
 */
public class JettyCracResource {

	// the org.crac.Resource is implemented as an inner class and kept as a strong
	// reference to avoid that it is garbage collected after the registration.
	private Resource cracHandler;

	public JettyCracResource(JettyBackedWhiteboardComponent jettyComponent) {
		cracHandler = new Resource() {
			@Override
			public void beforeCheckpoint(Context<? extends Resource> context) {
				Server jettyServer = jettyComponent.getJettyServer();
				if (jettyServer != null && !jettyServer.isStopped()) {
					// Stop the connectors only and keep the expensive application running
					Arrays.asList(jettyServer.getConnectors()).forEach(c -> LifeCycle.stop(c));
				}
			}

			@Override
			public void afterRestore(Context<? extends Resource> context) {
				Server jettyServer = jettyComponent.getJettyServer();
				if (jettyServer != null && !jettyServer.isStopped()) {
					Arrays.asList(jettyServer.getConnectors()).forEach(c -> LifeCycle.start(c));
				}
			}
		};
		Core.getGlobalContext().register(cracHandler);
	}
}
