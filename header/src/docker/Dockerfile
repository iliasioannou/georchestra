FROM jetty:9-jre8

ENV XMS=256M XMX=512M

RUN java -jar "$JETTY_HOME/start.jar" --create-startd --add-to-start=jmx,jmx-remote,stats

ADD . /

# Temporary switch to root
USER root

RUN chown jetty:jetty /etc/georchestra

# Restore jetty user
USER jetty

VOLUME [ "/tmp", "/run/jetty" ]

CMD ["sh", "-c", "exec java \
-Djava.io.tmpdir=/tmp/jetty \
-Dgeorchestra.datadir=/etc/georchestra \
-Xms$XMS -Xmx$XMX \
-XX:-UsePerfData \
${JAVA_OPTIONS} \
-jar /usr/local/jetty/start.jar"]
