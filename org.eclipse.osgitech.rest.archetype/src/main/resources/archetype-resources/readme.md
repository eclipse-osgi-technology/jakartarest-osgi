# Example Project

## Build project

You need to build the whole *org.eclipse.osgitech.rest* project first from the parent.

* Call `mvn clean install` to build the example.
* Check, if there are changes in the launch configuration. This can be executed when e.g. the run command fails: `mvn bnd-resolver:resolve`
* To test the project call: `mvn bnd-run:run`
* Find the application under: [http://localhost:8081/demo/rest/hello-http-whiteboard](http://localhost:8081/demo/rest/hello-http-whiteboard)

