#!/bin/bash
set -o errexit
set -o nounset
set -x verbose

pandoc -s using-octvolavg.md -o using-octvolavg.html
