# Neutrino Load Balancer - Configuration

[Project Documentation - Including design and features](wiki.vip.corp.ebay.com/display/neutrino)

## Configuring SLB or ESB

We use (primarily) the [Typesafe Configuration library](https://github.com/typesafehub/config), which uses a cascading configuration resolved by classpath.

In essence, the core provides a set of default configuration values in reference.conf, and each application can override with:
1) Their own reference.conf or application.conf files in the root of their classpath
2) An application.conf file in the runtime working directory
3) A custom .conf at a user-defined location within the user's classpath.