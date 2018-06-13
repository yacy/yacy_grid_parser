#!/usr/bin/env sh
cd "`dirname $0`"
cd ../data
KILLFILE="parser-8500.kill"
PIDFILE="parser-8500.pid"

# first method to terminate the process
if [ -f "$KILLFILE" ];
then
   rm $KILLFILE
   echo "termination requested, waiting.."
   # this can take 10 seconds..
   sleep 10
fi

# second method to terminate the process
if [ -f "$PIDFILE" ];
then
   fuser -k $PIDFILE
fi

# check if file does not exist any more which would be a sign that this has terminated
if [ ! -f "$PIDFILE" ];
then
   echo "process terminated"
fi

