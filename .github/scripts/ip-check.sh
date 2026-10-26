#!/usr/bin/env bash
#*********************************************************************
# Copyright (c) 2023-06-22 Data In Motion Consulting and others
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#**********************************************************************
set -ev

./mvnw --batch-mode --version
# test modules are not published, their dependencies are not distributed
./mvnw --batch-mode --no-transfer-progress org.eclipse.dash:license-tool-plugin:license-check -Ddash.fail=true -Ddash.summary=DEPENDENCIES -Peclipse-licenses-check \
  -pl '!org.eclipse.osgitech.rest.tck,!org.eclipse.osgitech.rest.tests,!org.eclipse.osgitech.rest.servlet.whiteboard.tests' "$@"
