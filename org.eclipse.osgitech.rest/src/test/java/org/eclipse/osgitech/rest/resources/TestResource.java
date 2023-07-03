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
package org.eclipse.osgitech.rest.resources;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * 
 * @author Mark Hoffmann
 * @since 14.07.2017
 */
@Path("test")
@Consumes({"yaml"})
@Produces({"xml", "json"})
public class TestResource {
	
	@POST
	@PUT
	@Produces("text")
	public Response postAndOut() {
		return Response.ok().build();
	}
	
	@POST
	@Path("pdf")
	@Consumes("pdf")
	@Produces("text")
	public Response postMe(String text) {
		return Response.ok().build();
	}
	
	@GET
	public Map<String, Integer> getValue(Map<String, Integer> input) {
		return Map.of("test", input.getOrDefault("input", 42));
	}

	protected String helloWorld() {
		return "hello world";
	}
}
