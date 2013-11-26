package com.ventyx.servicesuite.boltsfx.gui

import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.stage.WindowEvent
import scala.language.implicitConversions
import scala.util.Try
import java.io.IOException
import scalafx.scene.image.Image


object Main extends JFXApp {
  import scalafx.Includes._

  val applicationIcon = Try(getClass.getResource("/com/ventyx/servicesuite/images/bldserver.png").toExternalForm).getOrElse(throw new IOException("Cannot load resource: Application Icon"))
  val applicationStyleSheet = Try(getClass.getResource("/com/ventyx/servicesuite/css/default-skin.css").toExternalForm).getOrElse(throw new IOException("Cannot load resource: Application StyleSheet"))
  val aboutBoxImage = Try(getClass.getResource("/com/ventyx/servicesuite/images/bolt.jpg").toExternalForm).getOrElse(throw new IOException("Cannot load resource: Applicatiion AboutBox Image"))

  // def getStage():Stage = stage
  val boltSfxView = new BoltSfxView(applicationIcon, aboutBoxImage, applicationStyleSheet)

  stage = new PrimaryStage {
    title = "BoltSFX"
    minHeight = 590
    minWidth = 575
    height = 590
    width = 575
    icons ++= Seq(new Image(applicationIcon))

    scene = new Scene {
      stylesheets ++= Seq(applicationStyleSheet)
      minHeight = 590
      minWidth = 575
      content = boltSfxView
      onCloseRequest = (event:WindowEvent) => {
        println("Remember to run Compose")
      }
    }
  }
}
