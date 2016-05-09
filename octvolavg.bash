#!/bin/bash

# exit on error and don't allow the use of unset variables
set -o errexit
#set -o nounset

SRC_DIR=`dirname $0`
CP="OCT_Vol_Avg.jar"
for i in `find "${SRC_DIR}/lib" -name '*.jar'`; do CP="${CP}:${i}"; done

java -enableassertions -Xmx6g -cp "${CP}" org.jax.octvolavg.MainWindow

