# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OSGi Whiteboard implementation for Jakarta RESTful Web Services based on Eclipse Jersey. This is the reference implementation for the [OSGi Jakarta-RS Whiteboard specification](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.jakartars.html).

**Key Technologies:** Jersey 3.1.3, HK2 3.0.5, OSGi R8, Bnd 6.4.0, Java 11+

## Build Commands

```bash
# Build and run all tests
mvn verify

# Build specific module
mvn verify -pl org.eclipse.osgitech.rest

# Skip tests
mvn verify -DskipTests

# Run OSGi integration tests for a specific module
mvn verify -pl org.eclipse.osgitech.rest.tests
```

Tests use Bnd's testing framework with `.bndrun` files defining the OSGi test container. Integration tests run during the `post-integration-test` phase via `bnd-testing-maven-plugin`.

## Module Structure

| Module | Purpose |
|--------|---------|
| `org.eclipse.osgitech.rest` | Core whiteboard implementation |
| `org.eclipse.osgitech.rest.jetty` | Jetty adapter for standalone deployment |
| `org.eclipse.osgitech.rest.servlet.whiteboard` | OSGi Servlet Whiteboard adapter |
| `org.eclipse.osgitech.rest.config` | Default configuration fragment bundle |
| `org.eclipse.osgitech.rest.sse` | Server-Sent Events fragment |
| `org.eclipse.osgitech.rest.multipart` | Multipart request handling |
| `org.eclipse.osgitech.rest.tests` | Integration tests |
| `org.eclipse.osgitech.rest.servlet.whiteboard.tests` | Servlet whiteboard tests |
| `org.eclipse.osgitech.rest.tck` | TCK compliance tests |
| `org.eclipse.osgitech.rest.archetype` | Maven archetype for new projects |
| `org.eclipse.osgitech.rest.bnd.library` | Gradle Bnd library support |
| `org.eclipse.osgitech.rest.bnd.project.library` | Bndtools project templates |

## Architecture

The implementation follows the **OSGi Whiteboard pattern**: REST resources and extensions are registered as OSGi services and automatically discovered by the whiteboard runtime.

**Core package structure** (`org.eclipse.osgitech.rest`):
- `runtime/` - Core runtime components (JerseyServiceRuntime, whiteboard management)
- `provider/` - Configuration and runtime providers
- `binder/` - HK2 service binding for dependency injection
- `factories/` - Resource and extension factory patterns
- `annotations/` - OSGi capability/requirement annotations
- `proxy/` - Application proxy handling
- `dto/` - Data Transfer Objects for runtime introspection

**Configuration** is handled via:
- OSGi Configurator (JSON-based)
- OSGi Configuration Admin
- Configuration properties defined in `JerseyConstants.java`

## Code Conventions

- All source files require EPL-2.0 license headers (enforced via `.licenserc.yaml`)
- OSGi Declarative Services annotations for components
- Use `@JakartarsResource` and `@JakartarsName` annotations from `org.osgi.service.jakartars.whiteboard.propertytypes` for REST resources
- REST resources should use `ServiceScope.PROTOTYPE` for proper request scoping

## Usage

### Maven Dependencies

```xml
<!-- Core whiteboard implementation (required) -->
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest</artifactId>
  <version>${version}</version>
</dependency>

<!-- Choose ONE runtime adapter: -->
<!-- Option A: Jetty (standalone) -->
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.jetty</artifactId>
  <version>${version}</version>
</dependency>

<!-- Option B: OSGi Servlet Whiteboard -->
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.servlet.whiteboard</artifactId>
  <version>${version}</version>
</dependency>

<!-- Optional: Default configuration -->
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.config</artifactId>
  <version>${version}</version>
</dependency>

<!-- Optional: Server-Sent Events support -->
<dependency>
  <groupId>org.eclipse.osgi-technology.rest</groupId>
  <artifactId>org.eclipse.osgitech.rest.sse</artifactId>
  <version>${version}</version>
</dependency>
```

### Creating a REST Resource

Register REST resources as OSGi services with the whiteboard pattern:

```java
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsResource;
import org.osgi.service.jakartars.whiteboard.propertytypes.JakartarsName;

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

### Configuration (Jetty)

Configure via OSGi Configurator JSON:

```json
{
  ":configurator:resource-version": 1,
  "JakartarsWhiteboardComponent": {
    "jersey.port": 8081,
    "jersey.jakartars.whiteboard.name": "demo",
    "jersey.context.path": "demo"
  }
}
```

| Property | Description | Default |
|----------|-------------|---------|
| `jersey.schema` | URL schema | `http` |
| `jersey.host` | Host | `localhost` |
| `jersey.port` | Port | `8181` |
| `jersey.context.path` | Base context path | `/rest` |
| `jersey.jakartars.whiteboard.name` | Whiteboard name | `Jersey REST` |
| `jersey.disable.sessions` | Disable session handling | `true` |

### Configuration (Servlet Whiteboard)

```json
{
  "org.apache.felix.http~demo": {
    "org.osgi.service.http.port": 8081,
    "org.apache.felix.http.context_path": "demo",
    "org.apache.felix.http.runtime.init.id": "demowb"
  },
  "JakartarsServletWhiteboardRuntimeComponent~demo": {
    "jersey.jakartars.whiteboard.name": "Demo Jakarta REST Whiteboard",
    "jersey.context.path": "rest",
    "osgi.http.whiteboard.target": "(id=demowb)"
  }
}
```

### Gradle Bnd Library

Add `-library: jakartaREST` to your bnd instructions after including:

```
org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest.bnd.library:${version}
```

Use `-library: enableJakartaREST` in `.bndrun` files to auto-add all required bundles.
