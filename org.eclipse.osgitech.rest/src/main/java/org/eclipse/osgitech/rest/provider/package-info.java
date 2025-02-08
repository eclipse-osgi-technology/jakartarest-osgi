@Export
@ServiceProvider(value = RuntimeDelegate.class, register = RuntimeDelegateImpl.class)
@Referenced(org.glassfish.jersey.internal.RuntimeDelegateImpl.class)
package org.eclipse.osgitech.rest.provider;

import org.osgi.annotation.bundle.Export;

import aQute.bnd.annotation.spi.ServiceProvider;
import org.osgi.annotation.bundle.Referenced;
import jakarta.ws.rs.ext.RuntimeDelegate;

import org.glassfish.jersey.internal.RuntimeDelegateImpl;
