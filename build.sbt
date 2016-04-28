test in assembly := {}

//assemblyExcludedJars in assembly := ("specs2_2.11-2.3.12.jar")

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  val excludesJar = Set(
    "specs2_2.11-2.3.12.jar"
  )
  cp filter { jar => excludesJar.contains(jar.data.getName)}
}

