package com.abb.boltsfx

import java.io.{IOException, FileNotFoundException}
import java.io.{FileOutputStream, OutputStream, FileInputStream}
import java.util.Properties
import java.lang.IllegalArgumentException

import scala.reflect.runtime.universe.typeOf

import scalafx.Includes._
import scalafx.application.{Platform, JFXApp}
import scalafx.scene.image.Image
import scalafx.scene.Scene
import scalafx.stage.WindowEvent

import scalafxml.core.{NoDependencyResolver, DependenciesByType, FXMLView}


object BoltFXML extends JFXApp {

  val props = new java.util.Properties

  val applicationIcon: Image = new Image(getClass.getResource("/images/bldserver.png").toExternalForm)
  val checkmarkIcon: Image = new Image(getClass.getResource("/images/checkmark.png").toExternalForm)
  val warningIcon: Image = new Image(getClass.getResource("/images/warning.png").toExternalForm)
  
  val top: String = Utilities.getSystemEnv("TOP")
  val cdstop:String = Utilities.getSystemEnv("CDS_TOP")
  val os: String = Utilities.getSystemEnv("OS")
  val serviceSuiteVersion: String = Utilities.getSystemEnv("SERVICE_SUITE_VERSION")
  
  val dataloaderTemplate: String = "DataLoaderAutotestTemplate.xml"

  // if (top.isEmpty) throw new IllegalArgumentException("TOP not set!")
  // if (cdstop.isEmpty) throw new IllegalArgumentException("CDS_TOP not set!")
  // if (os.isEmpty) throw new IllegalArgumentException("OS not set!")
  // if (serviceSuiteVersion.isEmpty) throw new IllegalArgumentException("SERVICE_SUITE_VERSION not set!")

  val resource = getClass.getResource("/bolt.fxml")

  if (resource == null) throw new IOException("Cannot load resource: bolt.fxml")

  // Read properties file
  Utilities.readProfile(props, "/Users/caeadom/Documents/projects/uc/TOP")

  // Write properties file
  Utilities.writeProfile(props, "/Users/caeadom/Documents/projects/uc/TOP")

  // val root = FXMLView(getClass.getResource("/bolt.fxml"),
  //   new DependenciesByType(Map(
  //     typeOf[UnitConverters] -> new UnitConverters(InchesToMM, MMtoInches))))

  // val dependencies = new DependenciesByType(Map(typeOf[UnitConverters] -> new UnitConverters(InchesToMM, MMtoInches)))

  val root = FXMLView(resource, NoDependencyResolver)

  stage = new JFXApp.PrimaryStage() {
    title = "BoltFx"
    icons += applicationIcon
    // icons += new Image("/images/bldserver.png")  // also works
    resizable = false
    scene = new Scene(root){
      onCloseRequest = (event:WindowEvent) => println("Remember to run Compose")
      // stylesheets += this.getClass.getResource("/bolt.css").toExternalForm
      stylesheets += this.getClass.getResource("/my-modena.css").toExternalForm
    }
  }

  val primaryStage = stage

  import java.util.concurrent.atomic.AtomicBoolean
  import javafx.beans.{binding => jfxbb}
  import javafx.{concurrent => jfxc}
  import scalafx.concurrent.Task


  // NOTE: Object worker is created by extending `Task`.
  object Model {
    var shouldThrow = new AtomicBoolean(false)

    // NOTE: Object worker is created by extending `Task`.
    // ScalaFX `Task` cannot be directly instantiated since it is `abstract`, so we use `object` as a shortcut.
    // NOTE: ScalaFX `Task` is created by extending JavaFX `Task` that is passed to ScalaFX `Task` as the
    // delegate parameter (ScalaFX `Task` has no default constructor).
    object Worker extends Task(new jfxc.Task[String] {

      protected def call(): String = {
        println("JJJJJJJ")
        updateTitle("Example Task")
        updateMessage("Starting...")
        val total = 250
        updateProgress(0, total)

        for (i <- 1 to total) {
          try {
            Thread.sleep(20)
          } catch {
            case e: InterruptedException => return "Canceled at " + System.currentTimeMillis
          }
          if (shouldThrow.get) {
            throw new RuntimeException("Exception thrown at " + System.currentTimeMillis)
          }
          updateTitle("Example Task (" + i + ")")
          updateMessage("Processed " + i + " of " + total + " items.")
          updateProgress(i, total)
        }

        "Completed at " + System.currentTimeMillis
      }
    })
  }
}
