resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")

addSbtPlugin("com.hypertino" % "hyperbus-raml-sbt-plugin" % "0.2-SNAPSHOT")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")
