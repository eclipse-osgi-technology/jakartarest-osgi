# Contributing

Here are instructions to get you started.
They are probably not perfect, please let us know if anything feels wrong or incomplete.

## Project

This repository is part of the [Eclipse OSGi Technology Project](https://projects.eclipse.org/projects/technology.osgi-technology).
It contains a compatible implementation of the [OSGi Whiteboard Specification for Jakarta RESTful Web Services](https://github.com/osgi/org.osgi.service.jakartars).

## Reporting Issues

Issues can be reported in the [GitHub issue tracker](https://github.com/eclipse-osgi-technology/jakartarest-osgi/issues).
Please include the steps required to reproduce the problem if possible and applicable.

Security vulnerabilities must not be reported as public issues, see the [security policy](https://github.com/eclipse-osgi-technology/.github/blob/main/SECURITY.md).

## Build Environment

You need Java 17 or newer, the build is tested with Java 17, 21 and 25.
We use Maven, the repository includes the Maven wrapper `mvnw`.

- `./mvnw clean install` - builds all modules and runs the unit tests, the OSGi integration tests and the TCK
- `./.github/scripts/ip-check.sh` - runs the Eclipse Dash license check of the published modules

The modules `org.eclipse.osgitech.rest.tck`, `org.eclipse.osgitech.rest.tests` and `org.eclipse.osgitech.rest.servlet.whiteboard.tests` contain tests only and are not published.

We use [GitHub Actions](https://github.com/eclipse-osgi-technology/jakartarest-osgi/actions) for continuous integration, see `.github/workflows/cibuild.yml`.

## Workflow

All contributions come in through pull requests from a fork of this repository.

- Create an issue first for any significant change.
- Name the branch of a bug fix _issues/XXX_ where XXX is the number of the issue.
- Commit messages start with a short summary (max. 50 chars) written in the imperative.
  Reference the issue, e.g. `(#XXX)`; the reasoning belongs into the issue.
- Commits that fix or close an issue include a reference like `Closes #XXX` or `Fixes #XXX`.
- After every commit, the build must pass.
- Avoid unnecessary white space changes, they make reviewing much harder.

## Legal Considerations

Please read the [Eclipse Foundation policy on accepting contributions via Git](https://www.eclipse.org/projects/handbook/#resources-commit).

Your contribution cannot be accepted unless you have a signed [ECA - Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php) in place:

1. [Create an account at Eclipse](https://accounts.eclipse.org/user/register).
2. Add your GitHub user name in your account settings.
3. Sign the [Eclipse ECA](https://accounts.eclipse.org/user/eca).
4. Use the _same_ email address as your Eclipse account in your commits.

### Copyright Notice and License

This project is licensed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
Each source file starts with a copyright notice and the license header, the CI build checks it:

```java
/**
 * Copyright (c) <year> <copyright holders>
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     <name> - initial API and implementation
 */
```
