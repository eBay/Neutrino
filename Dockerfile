# Neutrino Docker file

FROM ubuntu:14.04

RUN apt-get -y update
RUN apt-get install -y nano openssh-server python supervisor unzip curl wget vim
RUN mkdir -p /var/run/sshd ; mkdir -p /var/log/supervisor
RUN apt-get install -y jq telnet

ADD supervisord.conf    /etc/supervisor/conf.d/supervisord.conf

# Install Open JDK 7
RUN apt-get install -y openjdk-7-jdk

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64



RUN mkdir -p /neutrino/logs && cd /neutrino \
    && mkdir -p /etc/neutrino

ADD run.conf    /etc/supervisor/conf.d/run.conf

RUN mkdir -p /ebay/software/packages/neutrino
COPY target/pack/lib /neutrino/lib 
COPY target/pack/bin /neutrino/bin
COPY src/pack/run.sh /neutrino/run.sh

RUN chmod 777 /neutrino/run.sh

# Expose all ports
EXPOSE 1-65535

CMD ["/usr/bin/supervisord"]
# default cmd to run 
