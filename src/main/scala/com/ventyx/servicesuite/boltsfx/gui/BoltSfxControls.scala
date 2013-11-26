package com.ventyx.servicesuite.boltsfx.gui

import scalafx.scene.{Scene, Group}
import scalafx.scene.control._
import scalafx.scene.input.KeyEvent
import scalafx.event.ActionEvent
import scalafx.stage.{Modality, Stage}
import scalafx.scene.image.{ImageView, Image}
import scalafx.scene.layout._
import scalafx.geometry.Pos

class BoltSfxControls(applicationIcon: String, aboutBoxImage: String, applicationStyleSheet: String) extends AnchorPane {
  import scalafx.Includes._

  def menuBar = new MenuBar {
    minWidth = 590.0
    prefWidth = 4000.0
    useSystemMenuBar = true
    styleClass ++= Seq("menu-bar")

    val fileMenu = new Menu("File") {
      val exitMenuItem = new MenuItem {
        text = "Exit"
        onAction = (event: ActionEvent) => {
          event.consume()
          // logger.info("exiting application")
          println("Remember to run Compose")
          sys.exit(0)
        }
      }

      items = Seq(exitMenuItem)
    }

    val helpMenu = new Menu("Help") {

      val helpMenuItem = new MenuItem {
        text = "About..."
        onAction = (event: ActionEvent) => {
          event.consume()
          // logger.info("show the about box")
          showAboutBox
        }
      }

      items = Seq(helpMenuItem)
    }

    menus = Seq(fileMenu, helpMenu)
  }

  // <Label layoutX="14.0" layoutY="39.0" styleClass="label-header" text="Select Build Steps" />
  private val buildStepsLabel = new Label {
    text = "Select Build Steps"
    layoutX = 14.0
    layoutY = 39.0
    styleClass ++= Seq("label-header")
  }

  // <Label layoutX="0.0" layoutY="0.0" styleClass="label-header" text="ODB Info" />
  // <CheckBox fx:id="compileCheckBox" layoutX="0.0" layoutY="0.0" mnemonicParsing="false" selected="true" text="Compile" />
  // <CheckBox fx:id="setupCheckBox" layoutX="81.0" layoutY="0.0" mnemonicParsing="false" selected="true" text="Setup" />
  // <CheckBox fx:id="testCheckBox" disable="false" layoutX="149.0" layoutY="0.0" mnemonicParsing="false" text="Test" />
  // <CheckBox fx:id="packageCheckBox" disable="false" layoutX="215.0" layoutY="0.0" mnemonicParsing="false" selected="false" text="Package" />

  private val compileCheckBox = new CheckBox {
    text = "Compile"
    selected = true
    layoutX = 0.0
    layoutY = 0.0
  }
  private val setupCheckBox = new CheckBox {
    text = "Setup"
    selected = true
    layoutX = 81.0
    layoutY = 0.0
  }
  private val testCheckBox = new CheckBox {
    text = "Test"
    selected = false
    layoutX = 149.0
    layoutY = 0.0
  }
  private val packageCheckBox = new CheckBox {
    text = "Compile"
    selected = false
    layoutX = 215.0
    layoutY = 0.0
  }
  // <Group id="Group" layoutX="14.0" layoutY="67.0">
  private val buildStepsGroup = new Group {
    layoutX = 14.0
    layoutY = 67.0
    children ++= Seq(compileCheckBox, setupCheckBox, testCheckBox, packageCheckBox)
  }

  // <Label layoutX="14.0" layoutY="39.0" styleClass="label-header" text="Select Build Steps" />
  // <TextField fx:id="odbUserTextField" layoutX="55.0" layoutY="28.0" prefWidth="322.0" promptText="ODB User" />
  // <TextField fx:id="odbPasswordTextField" layoutX="55.0" layoutY="58.0" prefWidth="322.0" promptText="ODB Password" />
  // <TextField id="odbInstanceTetField" fx:id="odbInstanceTextField" layoutX="55.0" layoutY="86.0" prefWidth="322.0" promptText="ODB Instance" />
  private val odbInfoLabel = new Label {
    text = "ODB Info"
    layoutX = 0.0
    layoutY = 0.0
    styleClass ++= Seq("label-header")
  }
  private val odbUserTextField = new TextField {
    promptText = "Enter ODB User"
    layoutX = 55.0
    layoutY = 28.0
    prefWidth = 322
    onAction = (event:KeyEvent) => {
      event.consume()
      odbUserTextFieldKeyReleased
    }
  }
  // <Label layoutX="24.0" layoutY="31.0" text="User" />
  private val odbUserLabel = new Label {
    layoutX = 24.0
    layoutY = 31.0
    text = "User"
  }
  private val odbPasswordTextField = new TextField {
    promptText = "Enter ODB Password"
    layoutX = 55.0
    layoutY = 58.0
    prefWidth = 322
    onAction = (event:KeyEvent) => {
      event.consume()
      odbPasswordTextFieldKeyReleased
    }
  }
  // <Label layoutX="0.0" layoutY="61.0" text="Password">
  private val odbPasswordLabel = new Label {
    layoutX = 0.0
    layoutY = 61.0
    text = "Password"
  }
  private val odbInstanceTextField = new TextField {
    promptText = "Enter ODB Instance"
    layoutX = 55.0
    layoutY = 86.0
    prefWidth = 322
    onAction = (event:KeyEvent) => {
      event.consume()
      odbInstanceTextFieldKeyReleased
    }
  }
  // <Label layoutX="3.0" layoutY="89.0" text="Instance" />
  private val odbInstanceLabel = new Label {
    layoutX = 3.0
    layoutY = 89.0
    text = "Instance"
  }

  // <Label fx:id="odbUserIconLabel" disable="false" layoutX="421.0" layoutY="31.0" text="Label" />
  // <Label fx:id="odbPasswordIconLabel" layoutX="421.0" layoutY="61.0" text="Label" />
  // <Label fx:id="odbInstanceIconLabel" layoutX="421.0" layoutY="91.0" text="Label" />
  private val odbUserIconLabel = new Label {
    layoutX = 421.0
    layoutY = 31.0
    disable = false
  }
  private val odbPasswordIconLabel = new Label {
    layoutX = 421.0
    layoutY = 61.0
    disable = false
  }
  private val odbInstanceIconLabel = new Label {
    layoutX = 421.0
    layoutY = 91.0
    disable = false
  }

  // <Group id="Group" layoutX="14.0" layoutY="99.0">
  private val odbInfoGroup = new Group {
    layoutX = 14.0
    layoutY = 99.0
    children ++= Seq(odbInfoLabel, odbUserTextField, odbUserLabel, odbPasswordTextField, odbPasswordLabel, odbInstanceTextField, odbInstanceLabel, odbUserIconLabel, odbPasswordIconLabel, odbInstanceIconLabel)
  }

  // <Group id="Group" layoutX="14.0" layoutY="220.0">
  // <Label layoutX="0.0" layoutY="3.0" text="Log Path" />
  // <TextField fx:id="logPathTextField" layoutX="53.0" layoutY="0.0" prefWidth="382.0" promptText="" text="\$TOP/logs" />

  private val logPathInfoLabel = new Label {
    text = "Log Path"
    layoutX = 0.0
    layoutY = 3.0
    styleClass ++= Seq("label-header")
  }
  private val logPathTextField = new TextField {
    promptText = "Enter path to log files"
    layoutX = 53.0
    layoutY = 0.0
    prefWidth = 382.0
    text = "$TOP/logs"
    onAction = (event:KeyEvent) => {
      event.consume()
      // odbInstanceTextFieldKeyReleased
      println("Log Path Text Field Action")
    }
  }
  private val logPathInfoGroup = new Group {
    layoutX = 14.0
    layoutY = 220.0
    children ++= Seq(logPathInfoLabel, logPathTextField)
  }

  //<TabPane id="optionsTabePane" fx:id="optionsTabPane" layoutX="14.0" layoutY="256.0" opacity="1.0" prefHeight="262.0" prefWidth="547.0" style=".tab-pane *.tab-header-background {&#10;    -fx-background-color: -fx-outer-border, -fx-inner-border, derive(-fx-color, -20%);&#10;    -fx-effect: innershadow(two-pass-box , rgba(0,0,0,0.6) , 4, 0.0 , 0 , 0);&#10;}" tabClosingPolicy="UNAVAILABLE">
  private val optionsTabPane = new TabPane {
    layoutX = 14.0
    layoutY = 256.0
    opacity = 1.0
    minHeight = 262.0
    minWidth = 547.0
    styleClass ++= Seq("tab-pane")
    //tabClosingPolicy = "UNAVAILABLE"

    val compileOptionsTab =  new Tab {
      text = "Compile Options"
      disable = false
      closable = false
      content = new AnchorPane {
        minHeight = 0.0
        minWidth = 0.0
        prefHeight = 217.0
        prefWidth = 480.0
      }
    }
    val setupOptionsTab =  new Tab {
      text = "Setup Options"
      disable = false
      closable = false
      content = new AnchorPane {
        minHeight = 0.0
        minWidth = 0.0
        prefHeight = 217.0
        prefWidth = 480.0
      }
    }
    val testOptionsTab =  new Tab {
      text = "Test Options"
      disable = true
      closable = false
      content = new AnchorPane {
        minHeight = 0.0
        minWidth = 0.0
        prefHeight = 217.0
        prefWidth = 480.0
      }
    }
    val packageOptionsTab =  new Tab {
      text = "Package Options"
      disable = true
      closable = false
      content = new AnchorPane {
        minHeight = 0.0
        minWidth = 0.0
        prefHeight = 217.0
        prefWidth = 480.0
      }
    }

    tabs ++= Seq(compileOptionsTab, setupOptionsTab, testOptionsTab, packageOptionsTab)
  }

  // <Button id="runBuildButton" fx:id="buildServerButton" layoutX="514.0" layoutY="537.0" mnemonicParsing="false" onAction="#buildServerButtonAction" text="Run" />
  private val runBuildButton = new Button {
    layoutX = 514.0
    layoutY = 537.0
    text = "Run"
    onAction = (event: ActionEvent) => {
      event.consume()
      // logger.info("show the about box")
      println("Run the build")
    }

  }

  content ++= Seq(menuBar, buildStepsLabel, buildStepsGroup, odbInfoGroup, logPathInfoGroup, optionsTabPane, runBuildButton)

  private def initState() {
    println("BoltSfx controls are initialised")
  }

  private def odbUserTextFieldKeyReleased() {
    println("ODB User Text Field Action")
  }

  private def odbPasswordTextFieldKeyReleased {
    println("ODB Password Text Field Action")
  }

  private def odbInstanceTextFieldKeyReleased {
    println("ODB Instance Text Field Action")
  }

  private def showAboutBox() {
    // Show as modal dialog
    new Stage {
      title = "About BoltSFX"
      height = 265
      width = 680
      icons ++= Seq(new Image(applicationIcon))

      initModality(Modality.WINDOW_MODAL)
      initOwner(Main.stage)
      scene = new Scene {
        stylesheets ++= Seq(applicationStyleSheet)
        root = new BorderPane {
          styleClass ++= Seq("root")
          center = new AnchorPane {
            content = Seq(
              /*
              <Label layoutX="236.0" layoutY="25.0" styleClass="labelHeading, label-header" text="Bolt" />
              <Label layoutX="236.0" layoutY="55.0" styleClass="labelHeading, label-header" text="The FASTEST way to build and setup a development server" />
              <Label layoutX="236.0" layoutY="85.0" styleClass="labelHeading, label-header" text="Product Version" />
              <Label layoutX="236.0" layoutY="112.0" styleClass="labelHeading, label-header" text="Service Suite Release" />
              <Label layoutX="236.0" layoutY="145.0" styleClass="labelCopyright, label-copyright" text="&lt;html&gt;&amp;copy;2013 Ventyx, an ABB Company. All Rights Reserved." />
              <Label fx:id="productVersionTextLabel" layoutX="396.0" layoutY="87.0" text="1.10" />
              <Label fx:id="serviceSuiteVersionTextLabel" layoutX="396.0" layoutY="111.0" text="9.2.1" />
              <Button fx:id="closeAboutButton" layoutX="592.0" layoutY="184.0" mnemonicParsing="false" onAction="#closeButtonAction" text="Close" />
              */
              new ImageView {
                fitWidth = 200.0
                fitHeight = 150.0
                layoutX = 15.0
                layoutY = 25.0
                preserveRatio = true
                pickOnBounds = true
                image = new Image(aboutBoxImage)
              },
              new Label {
                text = "Bolt"
                layoutX = 236.0
                layoutY = 25.0
                styleClass ++= Seq("label-header")
              },
              new Label {
                text = "The FASTEST way to build and setup a development server"
                layoutX = 236.0
                layoutY = 55.0
                styleClass ++= Seq("label-geader")
              },
              new Label {
                text = "Product Version"
                layoutX = 236.0
                layoutY = 85.0
                styleClass ++= Seq("label-header")
              },
              new Label {
                text = "Service Suite Release"
                layoutX = 236.0
                layoutY = 112.0
                styleClass ++= Seq("label-header")
              },
              new Label {
                text = "Copyright 2013 Ventyx, an ABB Company. All Rights Reserved."
                layoutX = 236.0
                layoutY = 145.0
                styleClass ++= Seq("label-copyright")
              },
              new Button {
                text = "Close"
                layoutX = 592.0
                layoutY = 184.0
                onAction = (evt: ActionEvent) => {
                  // TODO: code to close the dialog
                }
              }
            )
          }
        }
      }
    }.showAndWait()
  }

  hgrow = Priority.ALWAYS
  initState()
}
