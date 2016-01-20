<a href="http://neutrinoslb.github.io/"><img alt="Neutrino-logo" src="http://neutrinoslb.github.io/images/neutrino-logo.png" width="325"></a>

 [![Build Status](https://travis-ci.org/eBay/Neutrino.svg?branch=master)](https://travis-ci.org/eBay/Neutrino) [![Apache V2.0 License](http://www.parallec.io/images/apache2.svg) ](https://github.com/eBay/Neutrino/blob/master/LICENSE) 

Neutrino is a highly modular, flexible software load balancers compared to HAProxy and NGINX. Neutrino is distributed as a jar file, so that it can be easily configured for any existing topology. It also had a very good Pipeline architectire and pool resolvers, so that Neutrino can configured for any load balancing purpose.

Ebay uses Neutrino SLB to do L7 routing for QA pools. Link to the [website](http://neutrinoslb.github.io/)

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

## How to run standalone
- Create the slb.conf in "/etc/neutrino". Please refer to a sample slb.conf for reference
-   [slb.conf](https://github.com/eBay/Neutrino/blob/master/src/main/resources/slb.conf)
- Run "target/pack/sl-b"
- Open hosts file and add canonical name(cname) entries
  - 127.0.0.1 cname1.com
  - 127.0.0.1 cnamewildcard.com
- Run a webserver at 9999 port and/or 9998 port. Least Connection balancer will make sure that to send the traffic only if the server is up
- Hit the URL curl cname1.com:8080
- Hit the URL curl cnamewildcard.com:8080/website

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

Current code coverage is 60.33%. Code Coverage Link :

[Code Coverage](https://codecov.io/github/eBay/Neutrino)


## Contributions

Neutrino is developed by eBay PaaS Team
