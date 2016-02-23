<a href="http://neutrinoslb.github.io/"><img alt="Neutrino-logo" src="http://neutrinoslb.github.io/images/neutrino-logo.png" width="325"></a>

 [![Build Status](https://travis-ci.org/eBay/Neutrino.svg?branch=master)](https://travis-ci.org/eBay/Neutrino) [![Apache V2.0 License](http://www.parallec.io/images/apache2.svg) ](https://github.com/eBay/Neutrino/blob/master/LICENSE) 

Neutrino is a software load balancer(SLB) is used by eBay to do L7 Switching and Load Balancing for eBay’s test infrastructure. It is build using Scala and Netty and it uses the Java Virtual Machine (JVM) as run-time environment.

Link to the [website](http://neutrinoslb.github.io/)

## Why another SLB
Ebay was looking for options to replace their hardware load balancers which are expensive and unable to keep up with the demand. There were two options, either take an open source product like HAProxy or build an in-house one. 

From a high level, SLB has to satisfy following requirements
- L7 Switching using canonical names
- L7 Switching using canonical names and url context
- L7 Switching based on Rules. For eg. Traffic might need to route based on HTTP header, based on authentication header value etc
- Layer 4 Switching
- Should be able to send the traffic logs to API endpoints
- Cluster management is automated using eBay PaaS and Network Topology is stored in a DB and can be accessed through API. SLB should be able to read the topology and reconfigure itself
- Load Balancing should support most common algorithms like Least Connection and Round Robin. The framework should be extensible to add more algorithms in the future.
- Should be able to run on a Bare Metal, VM or a container
- No traffic loss during reconfiguration

HAProxy is the most commonly used SLB across the industry. It is written in C and has a reputation for being fast and efficient (in terms of processor and memory usage).  It can do L4 Switching and  L7 Switching using canonical names and url context. But L7 Switching based on rules, sending log to API end point or adding new load balancing algorithms cannot be satisfied using HAProxy. Reading the configuration from a DB or a API can be achieved through another application, but not optimal. Extending HAProxy to support these features found to be tough. Adding additional load balancing algorithms is also tough in HAProxy. Those are the reasons forced eBay to think about developing a SLB in-house.

Neutrino was built keeping the above requirements in mind. It is build in Scala language using Netty Server. It can do L7 routing using canonical names, url context and rule based. It has highly extensible pipeline architecture so that, new modules can be hooked into the pipeline without much work. Developers can add new switching rules and load balancing options easily. New modules can be added to send the log to API end point or load the configuration file from a DB or API. It is using JVM runtime environment, so developers can use either Java or Scala to add modules.

## Prerequisites

Building the neutrino requires:
- JDK 1.7+
- SBT 0.13.7
- Scala 2.11+
- IntelliJ IDEA 13 (recommended)

## How to Build
- Checkout the Git Repo GitHub
- Goto neutrino-opensource dir
- Run the command "sbt pack"
- "sbt pack" command will build Neutrino and create the jar files. You can find all the final jar files in "target/pack/lib"

## How to run in a server
- Create the slb.conf in "/etc/neutrino". Please refer to a sample slb.conf for reference
-   [slb.conf](https://github.com/eBay/Neutrino/blob/master/src/main/resources/slb.conf)
- Run "target/pack/sl-b"
- Open hosts file and add canonical name(cname) entries
  - 127.0.0.1 cname1.com
  - 127.0.0.1 cnamewildcard.com
- Run a webserver at 9999 port and/or 9998 port. Least Connection balancer will make sure that to send the traffic only if the server is up
- Hit the URL curl cname1.com:8080
- Hit the URL curl cnamewildcard.com:8080/website
- Hit the URL http://localhost:8079 to access pool information

# How to run in Docker container
- Create the slb.conf in "/etc/neutrino". Please refer to a sample slb.conf for reference
- [slb.conf](https://github.com/eBay/Neutrino/blob/master/src/main/resources/slb.conf)
- Run the docker command
  - docker run -d --net=host  -v /etc/neutrino:/etc/neutrino  -t neutrinoslb/latest
  - --net=host : use the host network stack inside the container
  - -v /etc/neutrino:/etc/neutrino : Mount the volume inside the container
  - -t neutrinoslb/latest : Get the latest docker image from docker hub

## Jenkins Build

[Jenkins](https://travis-ci.org/eBay/Neutrino/)

## Code Coverage

Current code coverage is 64%. Code Coverage Link :

[Code Coverage](https://codecov.io/github/eBay/Neutrino)


## Contributions

Neutrino is served to you by Chris Brown, Blesson Paul and eBay PaaS Team.

## Questions

Please post your questions in [google forum](https://groups.google.com/forum/#!forum/neutrinoslb)

