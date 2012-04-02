#!/bin/bash

JARDIR="."
JARS="${JARDIR}/timingframing.jar:${JARDIR}/org.json.jar:${JARDIR}/util.jar:${JARDIR}/commons-cli-1.2.jar"

COMMAND="$1"
shift
if [ ${COMMAND} = "client" ]; then
   java -cp ${JARS} edu.uw.cs.cse461.sp12.timingframing.ConsoleClient $*
else
   java -cp ${JARS} edu.uw.cs.cse461.sp12.timingframing.Server $*
fi
   
