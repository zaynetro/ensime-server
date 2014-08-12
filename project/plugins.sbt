// WORKAROUND https://github.com/sbt/sbt/issues/1439
def plugin(m: ModuleID) =
  Defaults.sbtPluginExtra(m, "0.13", "2.10") excludeAll ExclusionRule("org.scala-lang")

libraryDependencies ++= Seq(
  plugin("com.timushev.sbt" % "sbt-updates" % "0.1.6"),
  plugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4"),
  plugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0"),
  plugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.5.0"),
  plugin("org.scoverage" %% "sbt-scoverage" % "0.99.5.1"),
  plugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")
  // https://github.com/typelevel/wartremover/issues/108
  //plugin("org.brianmckenna" %% "wartremover" % "0.9")
  // https://github.com/scoverage/sbt-coveralls/issues/18
  //plugin("org.scoverage" %% "sbt-coveralls" % "0.98.0")
)
