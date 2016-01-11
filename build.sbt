import sbt.Keys._
import sbt._

name := "neutrino-core"

version := "1.0.0-SNAPSHOT"

organization := "com.ebay.neutrino"

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-language:postfixOps")

crossPaths := false

fork in ThisBuild := true

parallelExecution in ThisBuild := false

// Library dependencies
libraryDependencies ++= Seq(
  // Basic dependencies
  "org.slf4j"                 %  "slf4j-api"                  % Version.slf4J,
  "com.typesafe.scala-logging"      %% "scala-logging-slf4j"         % Version.scalalogging,
  "com.lihaoyi"               %% "scalatags"                  % Version.scalatags,
  "com.typesafe"              %  "config"                     % Version.config,
  "com.google.guava"          %  "guava"                      % Version.guava,
  "com.google.code.findbugs"  %  "jsr305"                     % Version.jsr305,
  //
  // Functionality Frameworks
  "io.netty"                  %  "netty-all"                  % Version.netty,
  "com.codahale.metrics"      %  "metrics-annotation"         % Version.metrics,
  "com.codahale.metrics"      %  "metrics-graphite"           % Version.metrics,
  "nl.grons"                  %% "metrics-scala"              % Version.metricsScala,
  // For API
  "io.spray"              %%  "spray-can"                  % Version.spray,
  "io.spray"              %%  "spray-client"               % Version.spray,
  "io.spray"              %%  "spray-routing"              % Version.spray,
  "io.spray"              %% "spray-json"                 % Version.sprayJson,
  // Test resources
  "org.scalatest"             %% "scalatest"                  % Version.scalatest % "test",
  "com.typesafe.akka"         %% "akka-actor"                 % Version.akka  % "test",
  "org.slf4j"                 %  "slf4j-simple"               % Version.slf4J
)

coverageExcludedPackages := "com\\.twitter.*;com\\.ebay\\.neutrino\\.www.*"

packAutoSettings
