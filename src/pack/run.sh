#!/bin/sh
#
DIR=/neutrino

echo "Starting SLB application"
${DIR}/bin/sl-b > /${DIR}/logs/neutrino.log 2>&1
