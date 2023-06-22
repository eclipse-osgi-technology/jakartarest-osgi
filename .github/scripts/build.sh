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
./mvnw --batch-mode --no-transfer-progress install "$@"
