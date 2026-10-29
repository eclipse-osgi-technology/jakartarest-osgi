/**
 * Copyright (c) 2026 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.eclipse.osgitech.rest.tests;

import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Predicate;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;

/**
 * Helpers to wait for the {@link RuntimeDTO} to reflect the whiteboard services a test
 * registered. The runtime coalesces registrations that arrive within a short time window,
 * so the number of changecount updates a registration causes is not predictable. Tests
 * therefore wait for the state they need instead of for a number of events (#71). The DTO
 * is published after the application has been reloaded, so once the content is listed the
 * application serves it.
 */
final class RuntimeDTOs {

	private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

	private RuntimeDTOs() {
	}

	/**
	 * Returns the service id of the given registration
	 */
	static long serviceId(ServiceRegistration<?> registration) {
		return (Long) registration.getReference().getProperty(Constants.SERVICE_ID);
	}

	/**
	 * Polls the {@link RuntimeDTO} of the runtime until the condition holds or the timeout
	 * elapses. Fails the test in the latter case.
	 * @return the first {@link RuntimeDTO} satisfying the condition
	 */
	static RuntimeDTO await(JakartarsServiceRuntime runtime, Duration timeout, Predicate<RuntimeDTO> condition)
			throws InterruptedException {
		Instant deadline = Instant.now().plus(timeout);
		RuntimeDTO dto;
		do {
			dto = runtime.getRuntimeDTO();
			if (dto != null && condition.test(dto)) {
				return dto;
			}
			Thread.sleep(POLL_INTERVAL.toMillis());
		} while (Instant.now().isBefore(deadline));
		fail("The RuntimeDTO did not reach the expected state within " + timeout + ": " + dto);
		return null;
	}

	/**
	 * Whether the default application lists the resource with the given service id
	 */
	static boolean defaultApplicationHasResource(RuntimeDTO dto, long serviceId) {
		ApplicationDTO app = dto.defaultApplication;
		return app != null && app.resourceDTOs != null
				&& Arrays.stream(app.resourceDTOs).anyMatch(r -> r.serviceId == serviceId);
	}

	/**
	 * Whether the default application lists the extension with the given service id
	 */
	static boolean defaultApplicationHasExtension(RuntimeDTO dto, long serviceId) {
		ApplicationDTO app = dto.defaultApplication;
		return app != null && app.extensionDTOs != null
				&& Arrays.stream(app.extensionDTOs).anyMatch(e -> e.serviceId == serviceId);
	}

	/**
	 * Whether the runtime reports the extension with the given service id as failed
	 */
	static boolean hasFailedExtension(RuntimeDTO dto, long serviceId) {
		return dto.failedExtensionDTOs != null
				&& Arrays.stream(dto.failedExtensionDTOs).anyMatch(e -> e.serviceId == serviceId);
	}
}
