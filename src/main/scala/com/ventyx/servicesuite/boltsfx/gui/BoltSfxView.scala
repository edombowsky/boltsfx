package com.ventyx.servicesuite.boltsfx.gui

import scalafx.scene.layout._
import scalafx.geometry.Pos


class BoltSfxView(applicationIcon: String, aboutBoxImage: String, applicationStyleSheet: String) extends VBox {
  import scalafx.Includes._

  val boltSfxControls = new BoltSfxControls(applicationIcon: String, aboutBoxImage: String, applicationStyleSheet: String) {
    alignment = Pos.CENTER
    // minWidth = 200
    // maxWidth = 500
    hgrow = Priority.ALWAYS
    vgrow = Priority.ALWAYS
  }


  val boltSfxControlsLayout = new HBox {
    val lspacer = new Region {hgrow = Priority.SOMETIMES}
    val rspacer = new Region {hgrow = Priority.SOMETIMES}
    hgrow = Priority.ALWAYS
    vgrow = Priority.ALWAYS
    content = Seq(lspacer, boltSfxControls, rspacer)
  }

  // val otherControlsLayout = new HBox {
  //   val spacer = new Region {hgrow = Priority.ALWAYS; pickOnBounds = false}
  //   pickOnBounds = false
  //   hgrow = Priority.ALWAYS
  //   vgrow = Priority.ALWAYS
  //   alignment = Pos.BOTTOM_CENTER
  //   spacing = 6
  //   content = Seq(openFilesBtn, spacer, playlistSettingsBtn, deleteFilesBtn)
  // }

  val mainControlsLayout = new AnchorPane {
    //styleClass ++= Seq("player-controls-bg")
    hgrow = Priority.ALWAYS
    // minHeight = 90
    // maxHeight = 90
    content = Seq(boltSfxControlsLayout)
  }

//  def onWidthUpdated(oldw:Double, neww:Double) = {
//    val oldWidth = math.max(oldw, stage.minWidth)
//    val newWidth = math.max(neww, stage.minWidth)
//  }

  styleClass ++= Seq("root")
  vgrow = Priority.ALWAYS
  hgrow = Priority.ALWAYS
  content = Seq(mainControlsLayout)
}
