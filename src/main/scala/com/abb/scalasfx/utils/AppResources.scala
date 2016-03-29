package com.ventyx.servicesuite.boltsfx.util

import scala.util.Try
import java.io.IOException

trait AppResources {
  val applicationIcon = Try(getClass.getResource("/com/ventyx/servicesuite/images/bldserver.png").toExternalForm).getOrElse(throw new IOException("Cannot load resource: Application Icon"))
  val applicationStyleSheet = Try(getClass.getResource("/com/ventyx/servicesuite/css/default-skin.css").toExternalForm).getOrElse(throw new IOException("Cannot load resource: Application StyleSheet"))
  val aboutBoxImage = Try(getClass.getResource("/com/ventyx/servicesuite/images/bolt.jpg").toExternalForm).getOrElse(throw new IOException("Cannot load resource: Applicatiion AboutBox Image"))

  val PREF_WIDTH  = 600
  val PREF_HEIGHT = 500
}
