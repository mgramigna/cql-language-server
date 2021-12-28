#!/usr/bin/env bash

# Fail if any command fails or if there are unbound variables and check syntax
set -euxo pipefail
bash -n "$0"

CMD="mvn test -T 4 -B -V"

if [[ ! -z "$TRAVIS_TAG" ]]
then
    CMD="$CMD -P ci"
fi

eval $CMD
