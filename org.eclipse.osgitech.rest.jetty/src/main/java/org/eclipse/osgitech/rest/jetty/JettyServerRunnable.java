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
package org.eclipse.osgitech.rest.jetty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * Runnable to start a Jetty server in a different thread
 * 
 * @author Stefan Bischof, Mark Hoffmann
 * @since 12.07.2017
 */
public class JettyServerRunnable implements Runnable {

	private final Server server;
	private final int port;
	private CountDownLatch awaitStart = new CountDownLatch(1);;
	private Throwable throwable;
	private JettyBackedWhiteboardComponent.State state = JettyBackedWhiteboardComponent.State.INIT;
	private Logger logger = Logger.getLogger("o.g.r.j.JettyServerRunnable");

	public JettyServerRunnable(Server server, int port) {
		this.server = server;
		this.port = port;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (server == null) {
			throwable = new RuntimeException("No server available to start");
			state = JettyBackedWhiteboardComponent.State.EXCEPTION;
			return;
		}
		try {

			logger.info("Started Jersey server at port " + port + " successfully try http://localhost:" + port);

			// why here? awaitStart.countDown();
			server.addEventListener(new LifeCycle.Listener() {
				@Override
				public void lifeCycleStopping(LifeCycle lifeCycle) {
					logger.info("lifeCycleStopping");
				}
				
				@Override
				public void lifeCycleStopped(LifeCycle lifeCycle) {
					state = JettyBackedWhiteboardComponent.State.STARTED;
					logger.info("lifeCycleStopped");
					
				}
				
				@Override
				public void lifeCycleStarting(LifeCycle lifeCycle) {
					logger.info("lifeCycleStarting");
				}
				
				@Override
				public void lifeCycleStarted(LifeCycle lifeCycle) {
					logger.info("lifeCycleStarted");
					state = JettyBackedWhiteboardComponent.State.STARTED;
					awaitStart.countDown();
					
				}
				
				@Override
				public void lifeCycleFailure(LifeCycle lifeCycle, Throwable throwable) {
					logger.info("lifeCycleFailure");
					state = JettyBackedWhiteboardComponent.State.EXCEPTION;
					JettyServerRunnable.this.throwable = throwable;
				}
			});
			server.start();
			server.join();
		} catch (Exception e) {

			throwable = new RuntimeException("Error starting Jersey server on port " + port, e);
			state = JettyBackedWhiteboardComponent.State.EXCEPTION;
		} finally {
			server.destroy();
		}
	}

	/**
	 * Returns the Throwable or null.
	 * 
	 * @return the getThrowable
	 */
	public Throwable getThrowable() {
		return throwable;
	}

	/**
	 * Returns the state.
	 * 
	 * @return the state
	 */
	public JettyBackedWhiteboardComponent.State getState() {
		return state;
	}

	public boolean isStarted(long timeout, TimeUnit unit) {
		try {
			
			boolean started = awaitStart.await(timeout, unit);
			if (!started) {
				logger.info("Starting Jersey server - not startet properly in estimated time");
			}
			return started;
		} catch (InterruptedException e) {
			if (JettyBackedWhiteboardComponent.State.STARTED.equals(state)) {
				// InterruptedException did not hit the jettyServerRunnable
				return true;
			}
		}
		return false;
	}

}
