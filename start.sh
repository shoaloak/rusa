#!/bin/sh


# this creates the distanceTree
RUN java \
    -jar rusa-jar-with-dependencies.jar \
    --file vulnserver.jar\
    --target ${PREFIX}${TARGET} \

# This executes.
# we already have the DB running

# standalone (restler-mod)
=distanceTree=distance_tree.json,mode=standalone

# Rusa
=distanceTree=distance_tree.json,mode=synergy
# we use distance_tree of vuln1

java -javaagent:target/rusa-jar-with-dependencies.jar=target.json -jar target.jar
