# OSGi Technology Whiteboard Implementation for Jakarta RESTful Web Services

[![CI Build](https://github.com/eclipse-osgi-technology/jakartarest-osgi/actions/workflows/cibuild.yml/badge.svg?branch=main)](https://github.com/eclipse-osgi-technology/jakartarest-osgi/actions/workflows/cibuild.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.eclipse.osgi-technology.rest/org.eclipse.osgitech.rest)](https://central.sonatype.com/artifact/org.eclipse.osgi-technology.rest/org.eclipse.osgitech.rest)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/eclipse-osgi-technology/jakartarest-osgi/badge)](https://securityscorecards.dev/viewer/?uri=github.com/eclipse-osgi-technology/jakartarest-osgi)

This is a OSGi Whiteboard implementation for Jakarta RESTful Web Services based on Eclipse Jersey.

https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.jakartars.html

https://eclipse-ee4j.github.io/jersey/

This implementation is **compliant** in respect to the corresponding OSGi Whiteboard specification. API and TCK of the specification are maintained in [osgi/org.osgi.service.jakartars](https://github.com/osgi/org.osgi.service.jakartars).

It requires Java 17 or newer.

It uses:

* Jersey 3.1.3
* HK2 3.0.5 

When you want to run it, you currently have the choice between two different adapters / connectors:

* **org.eclipse.osgitech.rest.jetty** - The adapter to run the implementation with Jetty **or**
* **org.eclipse.osgitech.rest.servlet.whiteboard** - An adapter to run the implementation with the OSGi Servlet Whiteboard

To use it, you will need the following bundles:

* **org.eclipse.osgitech.rest** - The whiteboard implementation
* **org.eclipse.osgitech.rest.config** - The whiteboard implementation default configuration, when you want to use it
* **org.eclipse.osgitech.rest.sse** - An optional fragment for the use of server sent events


You will find the release artifacts at Maven Central and the snapshots at https://central.sonatype.com/repository/maven-snapshots/.

```
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest</artifactId>
  <version>${version}</version>
</dependency>
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.config</artifactId>
  <version>${version}</version>
</dependency>
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.sse</artifactId>
  <version>${version}</version>
</dependency>
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.jetty</artifactId>
  <version>${version}</version>
</dependency>
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.servlet.whiteboard</artifactId>
  <version>${version}</version>
</dependency>
```

For those, who use Gradle:

```
org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest:${version}
org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest.config:${version}
org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest.sse:${version}
```

## Jetty Setup

The Jakarta REST whiteboard can use the *Eclipse Jetty*. To run a simple example you'll need:

* *org.eclipse.osgitech.rest*
* *org.eclipse.osgitech.rest.config* - The default configuration
* *org.eclipse.osgitech.rest.jetty* - The Jetty Adapter

You can change various server setting by using the OSGi Configurator or the Configuration Admin like this:

```json
{
  ":configurator:resource-version": 1,
  
  "JakartarsWhiteboardComponent": 
  {
    "jersey.port": 8081,
    "jersey.jakartars.whiteboard.name" : "demo",
    "jersey.context.path" : "demo" 
  }
}
```

This would run the server at

http://localhost:8081/demo

The following properties are supported for configuring the Whiteboard on Jersey:

| Parameter | Description | Default |
| --- | --- | -- |
|`jersey.schema`| The schema under which the services should be available. | http | 
|`jersey.host`| The host under which the services should be available. | localhost | 
|`jersey.port`| The port  under which the services should be available. | 8181 | 
|`jersey.context.path`| The base context path of the whiteboard. | /rest | 
|`jersey.jakartars.whiteboard.name`| The name of the whiteboard| Jersey REST | 
|`jersey.disable.sessions`| Enable/disable session handling in Jetty.<br>Disabled by default as REST services are stateless. | `true` | 

The definition of these properties is located in [JerseyConstants](https://github.com/eclipse-osgi-technology/jakartarest-osgi/blob/main/org.eclipse.osgitech.rest/src/main/java/org/eclipse/osgitech/rest/provider/JerseyConstants.java).

**_Note:_**  
The default value for `jersey.context.path` is `/rest`. So if you don't configure a value via the _configurator.json_ file, your services will be available via the `rest` context path. This is also the case for a custom Jakarta-RS application. If you don't want to use a context path, you explicitly have to set it to an empty value.


Please note, that the Felix Jetty implementation runs the OSGi HTTP Service by default at port 8080. 

**It may come to an conflict, with the port in your configuration.** 

Therefore you may set the system property `org.osgi.service.http.port=-1` to deactivate the HTTP Service under port 8080.

## OSGi Servlet Whiteboard Setup

To take profit from an eventually running servlet whiteboard, there is an adapter / connector the attach your Jersey Jakarta REST applications to this whiteboard.

To run this you'll need:

* *org.eclipse.osgitech.rest*
* *org.eclipse.osgitech.rest.servlet.whiteboard* - The OSGi Servlet Whiteboard Adapter

You can change various setting by using the OSGi Configurator or the Configuration Admin like this:

```json
{
  "org.apache.felix.http~demo":
  {
    "org.osgi.service.http.port": 8081,
    "org.osgi.service.http.host": "localhost",
    "org.apache.felix.http.context_path": "demo",
    "org.apache.felix.http.name": "Demo HTTP Whiteboard",
    "org.apache.felix.http.runtime.init.id": "demowb"
  },
  "JakartarsServletWhiteboardRuntimeComponent~demo":
  {
    "jersey.jakartars.whiteboard.name" : "Demo Jakarta REST Whiteboard",
    "jersey.context.path" : "rest",
    "osgi.http.whiteboard.target" : "(id=demowb)"
  }
}
```

This would run the Jakarta REST Whiteboard implementation at:

http://localhost:8081/demo/rest

The first block `org.apache.felix.http~demo` is used to configure the _Apache Felix HTTP Service_ service factory. Details about the configuration options are available in the [Apache Felix HTTP Service Wiki](https://cwiki.apache.org/confluence/display/FELIX/Apache+Felix+HTTP+Service).

The second block `JakartarsServletWhiteboardRuntimeComponent~demo` is used to configure the whiteboard service factory with the Servlet Whiteboard. The following properties are supported for configuring the Whiteboard on Servlet Whiteboard:

| Parameter | Description | Default |
| --- | --- | -- |
|`jersey.context.path`| The base context path of the whiteboard. | / | 
|`jersey.jakartars.whiteboard.name`| The name of the whiteboard| Jersey REST | 
|`osgi.http.whiteboard.target`| Service property specifying the target filter to select the Http Whiteboard implementation to process the service.<br>The value is an LDAP style filter that points to the id defined in `org.apache.felix.http.runtime.init.id`. | - | 

The definition of these properties is located in [JerseyConstants](https://github.com/eclipse-osgi-technology/jakartarest-osgi/blob/main/org.eclipse.osgitech.rest/src/main/java/org/eclipse/osgitech/rest/provider/JerseyConstants.java).

Please note, that the Felix Jetty implementation runs the OSGi HTTP Service by default at port 8080. 

**It may come to an conflict, with the port in your configuration.** 

Therefore you may set the system property `org.osgi.service.http.port=-1` to deactivate the HTTP Service under port 8080.

## Example Resource

When using the Jakarta REST Whiteboard, you just have to register your REST resources and extensions as a service. There are some useful Meta-Annotations, that create component properties for you.

```java
@JakartarsResource
@JakartarsName("demo")
@Component(service = DemoResource.class, scope = ServiceScope.PROTOTYPE)
@Path("/")
public class DemoResource {

	@GET
	@Path("/hello")
	public String hello() {
		return "Hello World!";
	}

}
```

## Maven Example Archetype

The module *org.eclipse.osgitech.rest.archetype* contains a Maven template to create a sample project.

To create a sample project call:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=org.eclipse.osgi-technology.rest \
  -DarchetypeArtifactId=org.eclipse.osgitech.rest.archetype \
  -DarchetypeVersion=${version} \
  -DgroupId=<your-group-id> \
  -DartifactId=<your-artifact-id>
```

The generated project contains a ready to run [example](org.eclipse.osgitech.rest.archetype/src/main/resources/archetype-resources/readme.md).


## Gradle Bnd Library Support

When adding the Jakarta REST Library to you dependencies:

```
org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest.bnd.library:${version}
```

you can simply but the instruction `-library: jakartaREST` and you will find a repository in you bnd workspace after reloading the workspace.

It brings you all dependencies, you need to run Jersey. In addition to that it also brings a dependency to:

```
org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest.bnd.project.library:${version}
```

This library adds support for *bndrun* files. Calling the instruction `-library: enableJakartaREST` within a *bndrun* adds automatically all Jersey and Jakarta REST Whiteboard dependencies to the *runbundles* section.

If you use the library instruction within a *bnd.bnd* file, it adds the JakartaRs API to the buildpath.

Furthermore this dependency brings three bndtools project templates:

1. Jakarta REST Resource with a default configuration
2. Jakarta REST Resource with Jetty Runtime and a corresponding configuration file
3. Jakarta REST Resource with a configuration for the use with the OSGI Servlet Whiteboard

All these projects can be imported using the bndtools project wizard.

## Build

You need Java 17 or newer:

```bash
./mvnw clean install
```

This builds all modules and runs the unit tests, the OSGi integration tests and the TCK of the specification.
See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Release

The version lives in exactly one place, the `revision` property in the root `pom.xml`.
On `main` it is always a `-SNAPSHOT` version.

The CI build deploys:

* every push to `main` as SNAPSHOT to the Central snapshots repository,
* every push to `release` as release to Maven Central.

A release requires a passing Eclipse Dash IP check. To release e.g. `1.2.4` while `main` is on `1.2.4-SNAPSHOT`:

1. Merge `main` into `release` (a fast forward, there are no commits of its own on `release`) and push it.
2. The CI build deploys the version without `-SNAPSHOT`, i.e. `1.2.4`, tags the commit as `v1.2.4` and opens a pull request that bumps `main` to `1.2.5-SNAPSHOT`.
3. Merge that pull request. For a minor or major release, set the `revision` on `main` accordingly before.

The release fails when the tag already exists, so a version can only be released once.

**Note:** Releases up to 1.2.3 were made with the maven-release-plugin, so `release` still contains commits of its own. Before the first release with this process, reset `release` once to `main` (`git push --force origin main:release`).
