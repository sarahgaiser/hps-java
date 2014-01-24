#!/bin/sh

classpath=${project.basedir}/target/${project.artifactId}-${project.version}-bin.jar
prod="java -Xmx1024m -classpath "$classpath" org.lcsim.hps.monitoring.MonitoringApplication $@"
# -c 100"
echo $prod
exec $prod
