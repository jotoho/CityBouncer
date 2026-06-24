#!/bin/bash

set -euxo pipefail

mvn clean compile assembly:single
java -jar target/citybouncer-1.0-SNAPSHOT-jar-with-dependencies.jar ./config.json
