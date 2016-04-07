package com.abb.boltsfx

import java.io.File

import scala.util.matching.Regex._

import scalafx.application.Platform
import scalafx.Includes._
import scalafx.scene.control.{CheckBox, TextField, TextArea, MenuItem, Alert, Button, Tab, Label}
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.Tooltip
import scalafx.event.ActionEvent
import scalafx.scene.input.KeyEvent
import scalafxml.core.macros.sfxml
import scalafx.scene.image.ImageView
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import javafx.beans.binding.StringBinding


@sfxml
class BoltSFxPresenter(
  private val bldRuntimeOptions: TextField,
  private val menuItemExit: MenuItem,
  private val menuItemAbout: MenuItem,
  private val runButton: Button,
  private val testTab: Tab,
  private val packageTab: Tab,
  private val compileTab: Tab,
  private val setupTab: Tab,
  private val odbUserTextField: TextField,
  private val odbPasswordTextField: TextField,
  private val odbInstanceTextField: TextField,
  private val odbUserIcon: ImageView,
  private val odbPasswordIcon: ImageView,
  private val odbInstanceIcon: ImageView,
  private val compileCheckBox: CheckBox,
  private val setupCheckBox: CheckBox,
  private val testCheckBox: CheckBox,
  private val packageCheckBox: CheckBox,
  private val packageAllTextField: TextField,
  private val packageVmCheckBox: CheckBox,
  private val cleanCheckBox: CheckBox,
  private val buildOdbCheckBox: CheckBox,
  private val portOffsetTextField: TextField,
  private val bldRuntimeTextArea: TextArea,
  private val dataLoaderBrowseButton: Button,
  private val dataloaderTemplateLabel: Label,
  private val runDataLoaderCheckBox: CheckBox,
  private val bldRuntimeOptionsLabel: Label,
  private val runBldRuntimeCheckBox: CheckBox,
  private val selectBuildStepsLabel: Label,
  private val junitCheckBox: CheckBox,
  private val findbugsCheckBox: CheckBox,
  private val packagePathTextField: TextField,
  private val logPathTextField: TextField,
  private val buildServerButton: Button) {

  // Filling the combo box
  // for (converter <- converters.available) {
  //   types += converter
  // }
  // types.getSelectionModel.selectFirst()

  // // Data binding
  // to.text <== new StringBinding {
  //   bind(from.text.delegate, types.getSelectionModel.selectedItemProperty)
  //   def computeValue() = types.getSelectionModel.getSelectedItem.run(from.text.value)
  // }

  val odbUser = BoltFXML.props.getProperty("odb_user") 
  val odbPassword = BoltFXML.props.getProperty("odb_password")
  val odbInstance = BoltFXML.props.getProperty("odb_instance")
  val portOffset = BoltFXML.props.getProperty("uid")
  val bldRuntime = BoltFXML.props.getProperty("bldruntime")

  var mDataloaderTemplate = BoltFXML.props.getProperty("dataloadertemplate")

  odbUser match {
    case a: String => odbUserTextField.setText(a)
    case _ => odbUserTextField.setText("")
  }

  odbPassword match {
    case a: String => odbPasswordTextField.setText(a)
    case _ => odbPasswordTextField.setText("")
  }

  odbInstance match {
    case a: String => odbInstanceTextField.setText(a)
    case _ => odbInstanceTextField.setText("")
  }

  validateOdbFields

  portOffset match {
    case a: String => portOffsetTextField.setText(a)
    case _ => portOffsetTextField.setText(Utilities.getSystemEnv("myID"))
  }

  bldRuntime match {
    case a: String => bldRuntimeTextArea.setText(a)
    case _ =>
  }

  mDataloaderTemplate match {
    case a: String => dataloaderTemplateLabel.setText(a)
    case _ => dataloaderTemplateLabel.setText(BoltFXML.dataloaderTemplate)
  }

  // Disable thes because by default they are disabled
  testTab.disable = true
  packageTab.disable = true

  def menuQuitApp(event: ActionEvent) {
    println("Remember to run Compose")
    Platform.exit()
  }

  def menuHelpAbout(event: ActionEvent) {
    new Alert(AlertType.Information) {
      initOwner(BoltFXML.primaryStage)
      title = "About BoltFx"
      headerText = None
      contentText = s""" Bolt
        |
        |The FASTEST way to build and setup a development server
        |
        |Product Version:           1.0
        |Service Suite Release:     9.4
        |
        |\u00A9 Copyright 1994-29016 ABB. All reights reserved.""".stripMargin
    }.showAndWait()
  }

  vssVersionSpecific

  // Close button event handler
  def onClose(event: ActionEvent) {    
    Platform.exit()
  }

  def handleTestCheckBox(event: ActionEvent) {

    val runTests: Boolean = testCheckBox.isSelected

    // Once the Test check box is selected, the following items must also be set and enabled
    // 1. Tests tab, and the JUnit check-box
    // 2. Build ODB check box
    testTab.disable = !runTests
    junitCheckBox.disable = !runTests
    findbugsCheckBox.disable = !runTests

    enableBuildButton
  }

  def handlePacakgeCheckBox(event: ActionEvent) {

    val runPackaging: Boolean = packageCheckBox.isSelected

    packageTab.disable = !runPackaging
    packagePathTextField.disable = !runPackaging

    enableBuildButton
  }

  def handleCompileCheckBox(event: ActionEvent) {
      
      val runCompile: Boolean = compileCheckBox.isSelected

      compileTab.disable = !runCompile
      cleanCheckBox.disable = !runCompile
      buildOdbCheckBox.disable = !runCompile

      enableBuildButton
  }

  def handleSetupCheckBox(event: ActionEvent) {
      
    val runSetup: Boolean = setupCheckBox.isSelected

    setupTab.disable = !runSetup
    
    runBldRuntimeCheckBox.disable = !runSetup

    if (runBldRuntimeCheckBox.isSelected) bldRuntimeTextArea.disable = false
    else bldRuntimeTextArea.disable = true

    runDataLoaderCheckBox.disable = !runSetup
    
    if ( runDataLoaderCheckBox.isSelected) dataLoaderBrowseButton.disable = false
    else dataLoaderBrowseButton.disable = true
  
    enableBuildButton
  }
  
  def handleOdbUserTextField(event: KeyEvent) = validateOdbFields
  
  def handleOdbPasswordTextField(event: KeyEvent) = validateOdbFields

  def handleOdbInstanceTextField(event: KeyEvent) = validateOdbFields

  def handleRunBldRuntimeCheckBox(event: ActionEvent) = bldRuntimeTextArea.disable = !runBldRuntimeCheckBox.isSelected

  def handleRunDataLoaderCheckBox(event: ActionEvent) = dataLoaderBrowseButton.disable = !runDataLoaderCheckBox.isSelected

  def handleDataLoaderBrowseButtonMouseClicked(event: ActionEvent) {

    val dlDir: java.io.File = new java.io.File(s"${BoltFXML.top}/../../SYSTEM/Support.WFM/DataLoader/")
    
    if (! dlDir.exists) {
     
      new Alert(AlertType.Warning) {
        initOwner(BoltFXML.primaryStage)
        title = "Warning"
        // headerText = "F"
        contentText = s"Dataloader directory: ${dlDir.getPath} does not exist."
      }.showAndWait()
    }
      
    val fileChooser = new FileChooser() {
      title = "Pick dataloader tempate"
      extensionFilters ++= Seq(new ExtensionFilter("DataLoader template (*.xml)", "*.xml"))
      initialDirectory = if (dlDir.exists) dlDir else new java.io.File(new java.io.File(".").getCanonicalPath)
    }

    val selectedFile = fileChooser.showOpenDialog(BoltFXML.primaryStage)
    println(s"selectedFile: ${selectedFile}")

    if (selectedFile != null) {
      mDataloaderTemplate = selectedFile.getName
      dataloaderTemplateLabel.setText(selectedFile.getName)
    }
  }

  def buildServerButtonMouseClicked(event: ActionEvent) {

    val runClean: Boolean = cleanCheckBox.isSelected
    val runCompile: Boolean = compileCheckBox.isSelected
    val runSetup: Boolean = setupCheckBox.isSelected
    val runBldRuntime: Boolean = runBldRuntimeCheckBox.isSelected
    val runDataLoader: Boolean = runDataLoaderCheckBox.isSelected
    val runTests: Boolean = testCheckBox.isSelected
    val runPackaging: Boolean = packageCheckBox.isSelected
    val runJunit: Boolean = junitCheckBox.isSelected
    val runFindBugs: Boolean = findbugsCheckBox.isSelected
    val bldOdb: Boolean = buildOdbCheckBox.isSelected
    val runPackageVm: Boolean = packageVmCheckBox.isSelected

    // Get BldRuntime and pkg_all options
    val bldRuntime: String = bldRuntimeTextArea.getText
    val pkgAll: String = packageAllTextField.getText

    // Get paths
    val offset: String = portOffsetTextField.getText.trim
    val logPath: String = expandPath(logPathTextField.getText.trim)
    val odbUser: String = odbUserTextField.getText.trim
    val odbPassword: String = odbPasswordTextField.getText.trim
    val odbInstance: String = odbInstanceTextField.getText.trim
    val pkgPath: String = expandPath(packagePathTextField.getText.trim)

    // Do not proceed if the logger was not initialized
    // if (! BoltUIApp.setupLogger(logPath)) return

    // log: Logger = BoltUIApp.getLogger

    // log.fine( "*** Starting Build ***")
    // log.fine("runClean = " + runClean)
    // log.fine("runCompile = " + runCompile)
    // log.fine("runSetup = " + runSetup)
    // log.fine("runBldRuntime = " + runBldRuntime)
    // log.fine("runDataLoader = " + runDataLoader)
    // log.fine("runTests = " + runTests)
    // log.fine("runPackaging = " + runPackaging)
    // log.fine("runPackageVM = " + runPackageVM)
    // log.fine("runJUnit = " + runJUnit)
    // log.fine("runFindBugs = " + runFindBugs)
    // log.fine("bldODB = " + bldODB)
    // log.fine("BldRuntime = [" + bldRuntime + "]")
    // log.fine("DataLoader template = [" + mDataLoaderTemplate + "]")
    // log.fine("Port offset = " + offset)
    // log.fine("logPath = " + logPath)
    // log.fine("odbUser = " + odbUser)
    // log.fine("odbPassword = " + odbPassword)
    // log.fine("odbInstance = " + odbInstance)
    // log.fine("pkgPath = " + pkgPath)
    // log.fine("pkgAll = [" + pkgAll + "]")

    var args: Map[String, String] = Map()
    
    // Mandatory parameters in order to have a Junit run
    if (runCompile) {
      
      args += ("-build" -> null)

      // Can only run BldDatabase during compile step.
      if (bldOdb) args += ("-buildodb" -> null)

      // Can only clean build during compile step
      if (runClean) args += ("-clean" -> null)
    }

    if (runSetup) {
        
      // Include the setup argument only if both BldRuntime and DataLoader are set, too.
      if (runBldRuntime && runDataLoader) args += ("-setup" -> null)

      if (runBldRuntime) {
        args += ("-setupbldruntime" -> null)

        // Include any args for BldRuntime
        if (bldRuntime != null && !bldRuntime.isEmpty) {
          args += ("-bldruntime" -> s""""${bldRuntime}"""")
          BoltFXML.props.setProperty("bldruntime", bldRuntime)
        }
      }

      if (runDataLoader) {
        args += ("-setupdataloader" -> null)
        args += ("-dataloadertemplate" -> mDataloaderTemplate)
        BoltFXML.props.setProperty("dataloadertemplate", mDataloaderTemplate)
      }
    }

    if (runTests && runJunit) args += ("-junit" -> null)

    if (runTests && runFindBugs) args += ("-findbugs" -> null)

    if (runPackaging) {
      if (pkgAll != null && !pkgAll.isEmpty) args += ("-package" -> s"""""${pkgAll}"""")
      else args += ("-package" -> null) // No packaging options

      if (runPackageVm) args += ("-packagevm" -> null)
    }

    // ODB credentials.
    if (!odbUser.isEmpty())
    {
      args += ("-user" -> odbUser)
      BoltFXML.props.setProperty("odb_user", odbUser)
    }
    
    if (!odbPassword.isEmpty())
    {
      args += ("-pass" -> odbPassword)
      BoltFXML.props.setProperty("odb_password", odbPassword)
    }
    
    if (!odbInstance.isEmpty())
    {
      args += ("-instance" -> odbInstance)
      BoltFXML.props.setProperty("odb_instance", odbInstance)
    }

    if (!offset.isEmpty())
    {
      args += ("-uid" -> offset)
      BoltFXML.props.setProperty("uid", offset)
    }

    // Log and Packaging Path
    if (!logPath.isEmpty()) args += ("-logpath" -> logPath)

    if (!pkgPath.isEmpty()) args += ("-packagepath" -> pkgPath)

    // Lastly, don't drop the ODB user once built
    args += ("-nodrop" -> null)

    // Save settings to profile
    Utilities.writeProfile(BoltFXML.props, BoltFXML.top)

println(args)
    // BoltDialog d = new BoltDialog(this.getFrame(), true, args)
    // d.setLocationRelativeTo(this.getFrame())
    // d.setVisible(true)

import scalafx.geometry.Insets
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.control.{ProgressBar, Dialog, ButtonType, PasswordField}
// import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout.{GridPane, VBox}
    
case class Result(username: String, password: String)
 
// Create the custom dialog.
val dialog = new Dialog[Result]() {
  initOwner(BoltFXML.primaryStage)
  title = "Building a Server..."
  headerText = None//"Look, a Custom Login Dialog"
}
 
val progressBar = new ProgressBar {
  prefWidth = 528 
  maxWidth = 32767
  progress = 0.0
  progress <== BoltFXML.Model.Worker.progress
}

val progressDescriptionLabel = new Label {
  prefWidth = 528 
  maxWidth = 32767
}

val consoleTextField = new TextArea() {
  prefWidth = 528 
  maxWidth = 32767
  editable = false
  text = """Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nulla vel massa sit amet nisl pulvinar hendrerit. Praesent vitae ultrices velit. In sed neque non est posuere semper. Curabitur id fermentum ante. Sed lorem elit, luctus eu erat in, consectetur ornare nunc. Fusce a ligula facilisis, tincidunt orci eu, tristique tortor. Pellentesque iaculis libero pretium accumsan iaculis.

Morbi quis risus nec metus accumsan condimentum convallis et lacus. Nunc eleifend eros tortor, quis blandit nisi vulputate vitae. Mauris vel magna auctor, aliquam tortor sit amet, egestas odio. Fusce pretium volutpat sodales. Vestibulum nisi tortor, dapibus at lobortis sed, egestas eu tellus. Sed quis nisl lacus. Praesent at commodo neque, vel molestie sapien. Vestibulum condimentum vehicula nisi. Vivamus vehicula facilisis congue. Ut in vestibulum nibh, eget ultricies magna. Cras lobortis lorem vel leo malesuada, id auctor ligula cursus. Maecenas vel volutpat nibh, quis mollis erat. Praesent justo ante, ornare quis ipsum eu, accumsan sollicitudin turpis. Cras at ex at nisi malesuada convallis eu eget ante. Integer fermentum molestie sollicitudin.

Cras ultrices nunc mattis arcu suscipit, ut consequat mauris aliquet. Pellentesque gravida purus id urna varius ultrices. Nulla facilisi. Sed sodales, neque nec feugiat tempor, neque felis lacinia magna, nec vehicula quam nibh dapibus nulla. Nunc hendrerit, erat ut malesuada hendrerit, velit nulla pharetra lorem, at ullamcorper erat nulla varius diam. Aenean dictum molestie erat eget venenatis. Sed vel pellentesque turpis. Etiam vitae lorem elit. Aliquam ligula orci, tincidunt eu lorem eget, finibus eleifend ex. Nulla sit amet tortor et diam vestibulum vulputate.

Praesent ut sem vel metus pretium sodales. Integer vitae placerat tellus. Etiam laoreet risus sit amet vestibulum vestibulum. In hac habitasse platea dictumst. Pellentesque blandit consectetur nisl vitae laoreet. Cras fringilla dolor leo, non rhoncus turpis malesuada lobortis. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Fusce eu eros augue. Proin placerat augue orci, et efficitur justo pellentesque in.

Etiam volutpat molestie sapien at volutpat. Nam nec mi sed sapien rutrum pellentesque a at nisl. Ut nec volutpat nunc. Nulla porttitor porta ornare. Duis eu vulputate arcu. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Donec at sodales quam, eu pellentesque velit. Aenean at ornare lorem. Sed sit amet ex mattis, efficitur ligula facilisis, semper nisi. Interdum et malesuada fames ac ante ipsum primis in faucibus. Sed vitae tellus mauris. Aenean porttitor luctus tortor, vel vehicula lorem lobortis sit amet. Fusce sapien lacus, posuere et scelerisque non, volutpat at risus. Quisque et mi faucibus, aliquam turpis auctor, tristique nunc. Praesent ullamcorper vulputate gravida. Pellentesque cursus risus ac massa fringilla, ut imperdiet sapien suscipit.

In in accumsan nisl, sit amet blandit lectus. Sed euismod convallis bibendum. Etiam gravida laoreet vehicula. Maecenas justo augue, eleifend sit amet porta a, dignissim at enim. Ut nec lacus felis. Donec auctor orci non facilisis efficitur. Proin aliquam lobortis augue vel mattis. Proin ultrices nisi porttitor, eleifend tellus quis, pretium nisi. Interdum et malesuada fames ac ante ipsum primis in faucibus. Nulla nec lacus semper, ultricies magna eu, pharetra lacus.

Suspendisse mattis ac nulla imperdiet finibus. Maecenas ut dui eu nunc sodales fermentum ut a felis. Cras dictum ipsum vel est accumsan tristique. Vestibulum tempus nibh at magna vestibulum porttitor. Duis dapibus ultricies dui nec ornare. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Aliquam sit amet magna ut erat imperdiet accumsan ac id ipsum. Vestibulum tellus leo, vehicula vel mi malesuada, gravida imperdiet lacus. Donec sit amet lacus viverra, dignissim quam at, varius eros.

Aliquam iaculis ligula non scelerisque facilisis. Morbi interdum, massa at sagittis hendrerit, lacus est gravida lacus, ac lobortis sapien ligula eget ex. Nunc egestas leo non nunc aliquet, ac posuere mi scelerisque. Duis pulvinar egestas vulputate. Duis accumsan velit quis tellus cursus gravida. Vestibulum sagittis nibh quis massa posuere lacinia. Donec quis elit ut metus suscipit placerat non in ante. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Phasellus lobortis dolor id neque bibendum facilisis. Pellentesque ac lacus nulla. Suspendisse finibus, tortor in commodo vulputate, lectus mi ultricies justo, eget scelerisque lectus urna et dui. Aenean placerat ligula ut purus facilisis sagittis.

Donec lobortis dolor hendrerit ligula sodales, at eleifend orci feugiat. Suspendisse ultricies est sed malesuada bibendum. Quisque vitae aliquam leo. In a ligula tristique, commodo ante et, ultrices lectus. Nullam quis orci ex. Duis non lorem vel lacus sodales varius vel sit amet nisl. Vivamus a sodales nunc. Phasellus varius odio eget nibh fermentum, ut mattis nulla vestibulum.

Integer mattis feugiat ipsum in sollicitudin. Ut nec diam magna. Vestibulum et nibh elit. Morbi tincidunt sapien nunc, eu consequat nisl pellentesque sed. Mauris nibh libero, finibus a efficitur id, mattis eu elit. Vivamus vitae sapien semper, imperdiet metus et, volutpat nisl. Integer nec dapibus felis, ut interdum neque. Quisque commodo suscipit eros eget tempus. Aliquam erat volutpat. Phasellus nec aliquet ante, at rhoncus est. Suspendisse non rutrum augue. Nulla ac turpis ac ante ultrices imperdiet. Mauris quis orci in sem aliquam molestie ut in metus. Curabitur vitae maximus felis. Phasellus risus augue, feugiat suscipit nunc mattis, varius tincidunt leo. Aliquam nibh turpis, volutpat eu turpis eget, laoreet pulvinar dolor.

Sed egestas ante ut augue laoreet dignissim. Etiam tempor ante et dolor efficitur consequat. Sed porttitor magna lorem, porta malesuada velit auctor sollicitudin. Praesent tincidunt a ex id fringilla. Integer auctor, est id malesuada sagittis, felis erat accumsan tortor, nec euismod nisl tortor a lacus. Curabitur scelerisque tellus pretium ultricies posuere. Duis eu lectus scelerisque, ornare erat ut, semper nisl.

Duis vel orci id quam semper consequat. Integer a neque dolor. Nam mollis volutpat odio elementum faucibus. Sed varius sodales varius. Maecenas non efficitur ligula. Nam aliquam quam sed tellus vulputate, nec condimentum libero tristique. Aliquam bibendum orci turpis, non tincidunt sapien accumsan ut. Integer lorem velit, pharetra ac auctor vel, consectetur ut augue.

Donec rutrum erat a condimentum dapibus. Integer semper, nisi quis consectetur ullamcorper, metus erat scelerisque ligula, quis molestie tellus tortor at libero. Nullam ac neque at purus malesuada porta ut vel nisi. Nam vehicula consequat faucibus. Suspendisse potenti. Etiam ultrices tincidunt velit sed sollicitudin. Nullam gravida mi in dolor blandit fringilla. Nulla tempor semper velit et facilisis. Donec feugiat arcu vitae pulvinar dictum. Ut placerat diam metus, ut viverra sapien tristique nec. Vivamus tincidunt quam non turpis pretium eleifend. Aliquam semper sem dapibus, commodo neque et, pellentesque lacus. Suspendisse id pharetra sapien. Nam vehicula nec neque bibendum posuere. Pellentesque ultrices, nulla sit amet dictum posuere, nulla erat feugiat sem, nec euismod est risus id mi. Mauris porttitor eget lacus ut sollicitudin.

Donec tincidunt mauris odio, id tempor elit pharetra vel. Proin eros sapien, ultricies ullamcorper tincidunt nec, tristique eu ligula. Integer sagittis, tortor non condimentum lacinia, velit nunc ultrices leo, sed elementum nibh quam non nulla. Sed eget magna interdum, scelerisque lorem vel, venenatis erat. Nunc elementum tortor vel sem laoreet pretium. Aliquam lobortis mauris nec erat bibendum mattis. Duis ut lacus sed nulla pulvinar rutrum. Nunc porttitor dapibus nulla fringilla hendrerit. Morbi nisl orci, pellentesque vehicula malesuada nec, feugiat non nisi. Nullam at tristique felis. Nulla commodo pharetra felis ac tincidunt. In bibendum porttitor dolor a scelerisque.

Duis vestibulum ex eget luctus porttitor. Sed faucibus purus sed ante tincidunt tempor. Aliquam eget urna id mi viverra congue et et tortor. Proin sed laoreet enim. Vivamus sollicitudin dui ante, sed ullamcorper nunc pulvinar id. Integer nibh elit, aliquet eget velit sit amet, ultrices condimentum velit. Donec porttitor fringilla rutrum. Maecenas quis lorem at enim blandit facilisis. Maecenas vitae malesuada diam. Nam ornare tincidunt aliquam. Pellentesque sit amet sem augue."""
}
val runComposeLabel = new Label {
  prefWidth = 193
  visible = false
  text = "Remember to run Compose"
}

// Set the button types.
val loginButtonType = new ButtonType("Login", ButtonData.OKDone)
dialog.dialogPane().buttonTypes = Seq(loginButtonType, ButtonType.Cancel)
 
// Create the username and password labels and fields.
val username = new TextField() {
  promptText = "Username"
}
val password = new PasswordField() {
  promptText = "Password"
}
 
val grid = new GridPane() {
  hgap = 10
  vgap = 10
  padding = Insets(20, 20, 10, 20)

  add(new Label("Progress"), 0, 0)
  add(progressBar, 0, 1) 
  add(progressDescriptionLabel, 0, 2)
  add(consoleTextField, 0, 3)
  add(runComposeLabel, 0, 4)
  // add(new Label("Username:"), 0, 0)
  // add(username, 1, 0)
  // add(new Label("Password:"), 0, 1)
  // add(password, 1, 1)
}
 
// Enable/Disable login button depending on whether a username was entered.
val loginButton = dialog.dialogPane().lookupButton(loginButtonType)
loginButton.disable = true
 
// Do some validation (disable when username is empty).
username.text.onChange { (_, _, newValue) => 
  loginButton.disable = newValue.trim().isEmpty
}
 
dialog.dialogPane().content = grid
 
// Request focus on the username field by default.
Platform.runLater(username.requestFocus())
 
// When the login button is clicked, convert the result to a username-password-pair.
dialog.resultConverter = dialogButton =>
  if (dialogButton == loginButtonType) Result(username.text(), password.text())
  else null
 
val result = dialog.showAndWait()
 
result match {
  case Some(Result(u, p)) => println("Username=" + u + ", Password=" + p)
  case None               => println("Dialog returned: None")
}
  }

  private def vssVersionSpecific {

    val vssVersion = BoltFXML.serviceSuiteVersion

    if (vssVersion.indexOf("93") != -1 || vssVersion.indexOf("94") != -1 || vssVersion.indexOf("95") != -1) {
      
      if (! vss9EnvCheck) {

        new Alert(AlertType.Warning) {
          initOwner(BoltFXML.primaryStage)
          title = "Warning proceeding may be futile"
          // headerText = "Look, an Error Dialog."
          contentText = "Service Suite 9+ environment variables not set"
        }.showAndWait()
      }
      
      bldRuntimeTextArea.setText("mahe_needed=0 cron_needed=1")
      bldRuntimeOptionsLabel.setText(s"BldRuntime Options VSS ${vssVersion}")
      selectBuildStepsLabel.setText(s"Select Build Steps for VSS ${vssVersion}")
    } else { // TODO expand for VSS8
    
      // new Alert(AlertType.Error) {
      //   initOwner(BoltFXML.primaryStage)
      //   title = "Fatal Error"
      //   // headerText = "Look, an Error Dialog."
      //   contentText = s"Bolt currently does not support VSS ${vssVersion}"
      // }.showAndWait()

      println(s"Bolt currently does not support VSS ${vssVersion}")
      sys.exit(1)
    }    
  }

  private def vss9EnvCheck: Boolean = {
    
    // VSS9 ODB env vars are defined by profile.service_suite
    // Ensure that they are set for VSS9
    val dbInstance: String = Utilities.getSystemEnv("ADVANTEX_dbInstance")
    val dbPassword: String = Utilities.getSystemEnv("ADVANTEX_dbPassword")
    val dbUser: String = Utilities.getSystemEnv("ADVANTEX_dbUser")
    // val dbInstance: Option[String] = sys.env.get("ADVANTEX_dbInstance")
    // val dbPassword: Option[String] = sys.env.get("ADVANTEX_dbPassword")
    // val dbUser: Option[String] = sys.env.get("ADVANTEX_dbUser")

    // if (dbInstance.isEmpty || dbPassword.isEmpty || dbUser.isEmpty) false
    // else true
    true
  }

  private def validateOdbFields {

    if (odbUserTextField.getText.isEmpty) odbUserIcon.setImage(BoltFXML.warningIcon)
    else odbUserIcon.setImage(BoltFXML.checkmarkIcon)

    if (odbPasswordTextField.getText.isEmpty) odbPasswordIcon.setImage(BoltFXML.warningIcon)
    else odbPasswordIcon.setImage(BoltFXML.checkmarkIcon)

    if (odbInstanceTextField.getText.isEmpty) odbInstanceIcon.setImage(BoltFXML.warningIcon)
    else odbInstanceIcon.setImage(BoltFXML.checkmarkIcon)

    enableBuildButton
  }

  private def enableBuildButton {

    if ( compileCheckBox.isSelected || setupCheckBox.isSelected || testCheckBox.isSelected || packageCheckBox.isSelected ) {
      if (buildOdbCheckBox.isSelected &&
          ! odbInstanceTextField.getText.isEmpty &&
          ! odbUserTextField.getText.isEmpty &&
          ! odbPasswordTextField.getText.isEmpty) {
    
        buildServerButton.disable = false
      } else if (! buildOdbCheckBox.isSelected) {
    
        buildServerButton.disable = false
      }
    } else {
      buildServerButton.disable = true
    }
  }

// import java.util.regex.Matcher
// import java.util.regex.Pattern

  /**
   * Expands environment variables within a path string.
   *
   * @see: http://www.scriptscoop.net/t/cf4bf1c73961/easiest-way-to-search-replace-with-regex-groups-in-scala.html
   *
   * @param path string to be expanded
   * @return the path string with any strings beginning with $ found in the users environment expanded
   */
  def expandPath(text: String): String = {

    val envMap = sys.env
    val varPattern = """\$([A-Za-z_0-9]+)""".r
    val mapper = (m: Match) => envMap get (m group 1) map (quoteReplacement(_))

    varPattern replaceSomeIn (text, mapper)
  }
}
