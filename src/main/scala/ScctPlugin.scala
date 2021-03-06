import sbt._
import Keys._

object ScctPlugin extends Plugin {

	/* defining scopes 'Coverage' & 'CoverageTest' */
	lazy val Coverage = config("coverage") extend(Compile) extend(Provided) extend(Optional)
	lazy val CoverageTest = config("coverage-test") extend(Test) extend(Coverage)
	/* defining scopes 'Coverage' & 'CoverageTest' */

  /* artifact filter used to attach scct compiler plugin jar using-Xplugin arg */
  val scctCompilerPluginFilter: NameFilter = (s: String) => s.startsWith("scct_")

	/* project values */
	var project_name = ""
	var project_docdir = ""
	var project_scaladir = ""
	/* project values */

	lazy val scctSettings: Seq[Project.Setting[_]] =
	  inConfig(Coverage)(Defaults.testSettings) ++
	  inConfig(CoverageTest)(Defaults.testSettings) ++
	  Seq(
			/* adding scct as a dependency */
			ivyConfigurations ++= Seq(Coverage, CoverageTest),

			resolvers += "scct repository" at "http://mtkopone.github.com/scct/maven-repo",

			libraryDependencies <<= (scalaVersion, libraryDependencies) { (sv, deps) =>
				val map = Map("2.9.1" -> "2.9.1", "2.9.0-1" -> "2.9.0-1","2.9.0" -> "2.9.0-1", "2.8.1" -> "2.8.0", "2.8.0" -> "2.8.0", "2.7.7" -> "2.7.7")
				val scctVersion = map.getOrElse(sv, sys.error("Unsupported Scala version " + sv))
				deps :+ "reaktor" % ("scct_" + scctVersion) % "0.1-SNAPSHOT" % "coverage"
			},
			/* adding scct as a dependency */

			/* configuring scope 'Coverage' */
			sources in Coverage <<= (sources in Compile).identity,

			scalacOptions in Coverage <++= update.map { report =>
				// gets the jars declared in the coverage configuration
    			val scctJars = report matching artifactFilter(name = scctCompilerPluginFilter)
    			scctJars.map("-Xplugin:" + _.getAbsolutePath) toSeq
			},
			/* configuring scope 'Coverage' */


			/* modifying tasks */
			TaskKey[Unit]("test") in Coverage <<= (TaskKey[Unit]("test") in CoverageTest).dependsOn(compile in Coverage),

			(target in Coverage in doc) <<= crossTarget / "coverage-report",

			(doc in Coverage) <<= (target in Coverage in doc) map { (d) => d },

			TaskKey[File]("doc") in Coverage <<= (TaskKey[File]("doc") in Coverage).dependsOn(test in Coverage),
			/* modifying tasks */

			/* get values */
			onLoad in Global <<= (onLoad in Global) ?? identity[State],

			{
			    val f = (s: State) => {

			    	val extracted: Extracted = Project.extract(s)
			    	import extracted._

			    	project_name = (name in currentRef get structure.data) match {
			    	  case Some(x) => x; case _ => "" }

			    	project_docdir = ((target in (currentRef, Coverage) in doc) get structure.data) match {
			    	  case Some(x) => (x / "coverage-report").absolutePath; case _ => "" }

			    	project_scaladir = (scalaSource in (currentRef, Coverage) get structure.data) match {
			    	  case Some(x) => x.absolutePath; case _ => "" }

			    	s
			    }

			    onLoad in Global ~= (f compose _)
			},
			/* get values */

			/* configuring scope 'CoverageTest' */
			sources in CoverageTest <<= (sources in Test),

			fullClasspath in CoverageTest <<=
				(fullClasspath in CoverageTest, classDirectory in Compile, classDirectory in Coverage)
					map { (cp, remove, add) =>
						cp.filter(_.data != remove) :+ Attributed.blank(add)
			},

			testOptions in CoverageTest += Tests.Setup { () =>
			  println("Setting props for " + project_name)
			  System.setProperty("scct.report.hook", "system.property")
			  System.setProperty("scct.project.name", project_name)
			  System.setProperty("scct.report.dir", project_docdir)
			  System.setProperty("scct.source.dir", project_scaladir)
			},

			testOptions in CoverageTest += Tests.Cleanup { () =>
			   	println("Generate report for " + project_name)
			  	val reportProperty = "scct.%s.fire.report".format(project_name)
			  	System.setProperty(reportProperty, "true")

			  	println("Wait for report completion.")

          val maxSleep = compat.Platform.currentTime + 60L*1000L

          while (sys.props(reportProperty) != "done" && compat.Platform.currentTime < maxSleep) {
            Thread.sleep(200L)
          }

          if (sys.props(reportProperty) != "done") {
            println("Timed out waiting for report")
          }
			}
			/* configuring scope 'CoverageTest' */
	  )
}