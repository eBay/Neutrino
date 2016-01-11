<a href="http://d-sjc-00541471:9999/website/neutrino-landing.html"><img alt="Neutrino-logo" src="http://d-sjc-00541471:9999/website/images/neutrino-logo.png" width="325"></a>

 [![Build Status](https://travis-ci.org/eBay/parallec.svg?branch=master)](https://ebayci.qa.ebay.com/neutrino-ci-85208/job/neutrino-opensource) [![Apache V2.0 License](http://www.parallec.io/images/apache2.svg) ](https://github.com/eBay/parallec/blob/master/LICENSE)

Neutrino is a highly modular, flexible software load balancers compared to HAProxy and NGINX. Neutrino is distributed as a jar file, so that it can be easily configured for any existing topology. It also had a very good Pipeline architectire and pool resolvers, so that Neutrino can configured for any load balancing purpose.

Ebay uses Neutrino SLB to do L7 routing for QA pools. Link to the [website](http://d-sjc-00541471:9999/website/neutrino-landing.html)

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
-   [slb.conf](https://github.corp.ebay.com/neutrino/neutrino-opensource/blob/master/src/main/resources/slb.conf)
- Run "target/pack/sl-b"
- Open hosts file and add canonical name(cname) entries
  - 127.0.0.1 cname1.com
  - 127.0.0.1 cnamewildcard.com
- Run a webserver at 9999 port and/or 9998 port. Least Connection balancer will make sure that to send the traffic only if the server is up
- Hit the URL curl cname1.com:8080
- Hit the URL curl cnamewildcard.com:8080/website


## Jenkins Build
[Jenkins](https://ebayci.qa.ebay.com/neutrino-ci-85208/job/neutrino-opensource)

## Code Coverage

Current code coverage is 60.33%. Code Coverage Link :

[Code Coverage](https://ebayci.qa.ebay.com/neutrino-ci-85208/job/neutrino-opensource/ws/target/scoverage-report/index.html)


## Contributions

Neutrino is developed by eBay PaaS Team
