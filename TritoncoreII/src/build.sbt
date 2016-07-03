scalaVersion := "2.11.7"

libraryDependencies += "edu.berkeley.cs" %% "chisel" % "latest.release"

unmanagedSourceDirectories in Compile += baseDirectory.value / "components"
