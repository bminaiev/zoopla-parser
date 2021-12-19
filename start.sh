#!/usr/bin/env bash

BASEDIR=$(dirname "$0")
cd $BASEDIR
gradle run --args config.txt --check-properties-in-db
