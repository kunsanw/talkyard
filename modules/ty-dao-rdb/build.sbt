name := "ty-dao-rdb"

organization := "com.debiki"

version := CurrentWorkingDirectory.versionFileContents

libraryDependencies ++= Seq(
  Dependencies.Play.json,
  Dependencies.Libs.postgresqlJbcdClient,
  Dependencies.Libs.flywaydb)

