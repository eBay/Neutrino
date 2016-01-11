import sbt._

// Add eBay-specific resolvers (TODO move this to CI)
resolvers ++= Seq(
  "Maven Repo" at "http://repo1.maven.org/maven2"
)


// Expensive resolver; Add instead in your local dev ~/.sbtconfig file
// addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.3.3")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("de.johoop" % "findbugs4sbt" % "1.3.0")

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.7.2")

checksums in update := Nil
