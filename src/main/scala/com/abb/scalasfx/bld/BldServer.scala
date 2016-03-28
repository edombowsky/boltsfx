/*
 * Copyright (c) 2016 ABB Enterprise Software. All rights reserved.
 *
 * Version 1.0
 *
 * This script builds a server environment, runs JUnit, and packages it up.
 * It is based on the steps documented at http://viki.ventyx.org/confluence/display/YVRPD/How+to+Build+a+Server+Development+Environment+VSS90
 *
 *
 * Examples:
 *
 * 1. To compile the server and run JUnit against an existing account: (Good for developer use)
 *
 *      BldServer -user <ODB user> -pass <ODB password> -instance <ODB instance> -buildjunit
 *
 *    This will compile, build the ODB, run dataloader and junit. Logs are put under $TOP. The ODB account WILL be dropped after
 *    it completes. Specify the -nodrop option to keep the ODB account.
 *
 *    Note that the user will still need to do a Compose deployment *AFTER* this script runs because the built-in deployment
 *    used by this script is not complete (it is only sufficient to run JUnit).
 *
 * 2. To compile the server only:
 *
 *      BldServer -user <ODB user> -pass <ODB password> -instance <ODB instance> -build
 *
 *    This only compiles the C++ and Java code. No BldRuntime, Data loading, or JUnit are run.
 *
 * 3. To package a server that has already been compiled:
 *
 *      BldServer -instance <ODB instance> -package "-no3rdParty" -packagepath $HOME/temp -logpath $HOME/logs
 *
 *    This will put the DVD images under $HOME/temp and all logs under $HOME/logs.
 *
 * 4. AutoTest uses the following commands:
 *
 *      BldServer -instance <ODB instance> -build -nodrop -logpath <logpath>
 *      BldServer -instance <ODB instance> -package "" -packagepath <packagepath> -logpath <logpath>
 *
 * Return codes:
 * 0  - normal exit
 * 1  - general error
 * 15 - JUnit build error
 *
 */
package com.abb.bolt

import java.util.Properties
import java.util.ArrayList
import java.util.Scanner
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.Date
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.IOException
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.io.FileReader
// import java.net._
import java.net.{InetAddress, UnknownHostException}
import java.sql.{SQLException, ResultSet, Statement, DriverManager, Connection}
import java.text.SimpleDateFormat

import scala.collection.immutable.Map

// import scala.sys.process.Process

object BuildStep extends Enumeration {
  type BuildStep = Value
  
  val NONE = Value
  val GET_ID = Value
  val CREATE_ODB_USER = Value
  val CHECK_ODB_USER = Value
  val JAM_EXPORTS = Value
  val BLD_DATABASE = Value
  val VSS_BUILD_CLEAN = Value
  val VSS_BUILD = Value
  val LOAD_PRIVILEGES = Value
  val BLD_RUNTIME = Value
  val MOVE_DIRS = Value
  val LOAD_COMPOSE = Value
  val START_LDAP = Value
  val DATA_LOADER = Value
  val STOP_LDAP = Value
  val POST_DATA_LOADER = Value
  val SYSTEM_ID = Value
  val JUNIT = Value
  val FINDBUGS = Value
  val COPY_LOGS = Value
  val DROP_ODB_USER = Value
  val DONE = Value
}
import BuildStep._

object PackageStep extends Enumeration {
  type PackageStep = Value

  val NONE = Value
  val VSS_BUILD_RELEASE = Value
  val JAM_RELEASE = Value
  val START_ENV = Value
  val CREATE_ODB_USER = Value
  val PKG_ALL = Value
  val PKG_VM = Value
  val PKG_APPLIANCE = Value
  val PKG_SOI = Value
  val PKG_SYSMON = Value
  val COPY_LOGS = Value
  val DONE = Value
}
import PackageStep._

case class BldServerConfig(
  user: Option[String] = None, 
  password: Option[String] = None, 
  instance: Option[String] = None,
  uid: Option[Int] = None,
  profileName: Option[String] = None,
  clean: Boolean = false,
  build: Boolean = false,
  buildOdb: Boolean = false,
  setup: Boolean = false,
  setupBldRuntime: Boolean = false,
  setupDataLoader: Boolean = false,
  dataLoaderTemplate: String = "DataLoaderAutotestTemplate.xml",
  buildJunit: Boolean = false,
  buildRuntime: Option[String] = None,
  junit: Boolean = false,
  findBugs: Boolean = false,
  pkg: Option[String] = None,
  packageVm: Boolean = false,
  packagePath: String = "TOP",
  packageServer: String = "usatl-s-ssvm09",
  noDrop: Boolean = false,
  logPath: String = "TOP")

object BldServer {

  private[bolt] class StopWatch {
    private var startTime: Long = 0
    private var stopTime: Long = 0
    private var running: Boolean = false

    def start {
      this.startTime = System.currentTimeMillis
      this.running = true
    }

    def stop {
      this.stopTime = System.currentTimeMillis
      this.running = false
    }

    def getElapsedTime: Long = {
      var elapsed: Long = 0L
      if (running) System.currentTimeMillis - startTime
      else stopTime - startTime
    }

    def getElapsedTimeSecs: Long = {
      var elapsed: Long = 0L
      if (running) (System.currentTimeMillis - startTime) / 1000
       else (stopTime - startTime) / 1000
    }
  }

  val parser = new scopt.OptionParser[BldServerConfig]("BldServer") {
    head(s"${hello.BuildInfo.name} v${hello.BuildInfo.version} is © 2016 by ${hello.BuildInfo.organization}")
    help("help") text("prints this usage text")
    version("version") text("prints version information")
    opt[String]('u', "user")
      .optional()
      .action { (x, c) => c.copy(user = Some(x)) }
      .text("ODB user (optional)")
    opt[String]('p', "pass")
      .optional()
      .action { (x, c) => c.copy(password = Some(x)) }
      .text("ODB password (optional)")
    opt[String]('i', "instance")
      .required()
      .action { (x, c) => c.copy(instance = Some(x)) }
      .text("ODB instance (e.g. dellr815c_r11203)")
    opt[Int]('u', "uid")
      .action { (x, c) => c.copy(uid = Some(x)) }
      .validate { x => if (x > 999) success else failure("Invalid uid, must be 3 digits or less") } 
      .text("User id, used for port offsets")
    opt[String]('o', "profile")
      .action { (x, c) => c.copy(profileName = Some(x)) }
      .text("Build profile file name")
    opt[Unit]('c', "clean")
      .optional()
      .action { (_, c) => c.copy(clean = true) }
      .text("Clean build artefacts before compiling")
    opt[Unit]('b', "build")
      .optional()
      .action { (x, c) => c.copy(build = true) }
      .text("Compile server code (C++ and Java) using vss_build")
    opt[Unit]('s', "setup")
      .optional()
      .action { (x, c) => c.copy(setup = true) }
      .text("Setup a development environment, Runs BldRuntime and Dataloader")
    opt[Unit]('r', "setupbldruntime")
      .optional()
      .action { (x, c) => c.copy(setupBldRuntime = true) }
      .text("Run BldRuntime")
    opt[Unit]('d', "setupdataloader")
      .optional()
      .action { (x, c) => c.copy(setupDataLoader = true) }
      .text("Run DataLoader")
    opt[String]('t', "dataloadertemplate")
      .optional()
      .action { (x, c) => c.copy(dataLoaderTemplate = x) }
      .text("Name of dataloader template to use. Default is DataLoaderAutotestTemplate.xml")
    opt[Unit]('J', "buildjunit")
      .optional()
      .action { (x, c) => c.copy(buildJunit = true) }
      .text("Compile, then run Setup and JUnit. Equivalent to Jenkins hourly runs")
    opt[String]('B', "bldruntime")
      .optional()
      .action { (x, c) => c.copy(buildRuntime = Some(x)) }
      .text("BldRuntime arguments (enclosed in double-quotes)")
    opt[Unit]('j', "junit")
      .optional()
      .action { (x, c) => c.copy(junit = true) }
      .text("RunJUnit tests")
    opt[Unit]('b', "findbugs")
      .optional()
      .action { (x, c) => c.copy(findBugs = true) }
      .text("Run FindBugs")
    opt[String]('k', "package")
      .optional()
      .action { (x, c) => c.copy(pkg = Some(x)) }
      .text("Package the server. Optional arguments can be passed but must be enclosed in double-quotes")
    opt[Unit]('v', "packagevm")
      .optional()
      .action { (x, c) => c.copy(packageVm = true) }
      .text("Package the VM appliance, SOI VM, and System Monitor (Hyperic) VM")
    opt[String]('t', "packagepath")
      .optional()
      .action { (x, c) => c.copy(packagePath = x) }
      .text("Location to store DVD images after packaging. Default is TOP")
    opt[String]('s', "packageserver")
      .optional()
      .action { (x, c) => c.copy(packageServer = x) }
      .text("Server to use for VM appliance packaging. Default is usatl-s-ssvm09")
    opt[Unit]('n', "nodrop")
      .optional()
      .action { (x, c) => c.copy(noDrop = true) }
      .text("Do NOT drop the ODB account after BldServer is run")
    opt[String]('l', "logpath")
      .optional()
      .action { (x, c) => c.copy(logPath = x) }
      .text("Path to store the logs. Default is TOP")
  }

/** 
  * Parse the command line and perform the specified doAction
  *
  * @param args  the command line arguments
  * @return 0 for success, positive non-zero for failure
  */
// def doAction(args: Array[String]): Int = {
  
//   parser.parse(args, BldServerConfig()) match {
//     case Some(config) =>
//       println("Starting BldServer with following parameters")
//       0

//       // val esxToolsVm = EsxToolsVm(config)

//       // if (esxToolsVm.vm == null) {
//       //   logger.error(s"${config.vmName} was not found")
//       //   esxToolsVm.si.getServerConnection.logout()
//       //   VmUtilities.VmNotFound
//       // } else {
//       //   config.command.getOrElse(EsxCommands.Nop) match {
//       //     case EsxCommands.Status => status(esxToolsVm)
//       //     case EsxCommands.PowerUp => powerUp(esxToolsVm)
//       //     case EsxCommands.PowerDown => powerDown(esxToolsVm)
//       //     case EsxCommands.CopyVirtualDisk => copyVirtualDisk(esxToolsVm)
//       //     case EsxCommands.ChangeMacAddress => changeMacAddress(esxToolsVm)
//       //     case EsxCommands.Destroy => destroy(esxToolsVm)
//       //     case EsxCommands.PowerDownOs => powerDownOs(esxToolsVm)
//       //     case EsxCommands.List =>
//       //       config.subCommand match {
//       //         case EsxCommands.Vms => listVms(esxToolsVm)
//       //         case EsxCommands.DataCentres => listDataCentres(esxToolsVm)
//       //         case _ => { println(s"Command [${config.command}:${config.subCommand}] not yet implemented..."); 1}
//       //       }
//       //     case _ => { println(s"Command [${config.command}] not yet implemented..."); 1}
//       //   }
//       // }

//     case None =>
//       // Arguments are bad, error message will have been displayed
//       // VmUtilities.Failure
//       1
//     }
//   }

  def main(args: Array[String]) {    
    parser.parse(args, BldServerConfig()) match {
      case Some(config) =>
        println("Starting BldServer")

        try {
          val sb: BldServer = new BldServer(config)
          val t: BldServer.StopWatch = new BldServer.StopWatch
        
          t.start
          val rc = sb.start
          t.stop
        
          val elapsed: Long = t.getElapsedTimeSecs
          val mins: Long = elapsed / 60
          val secs: Long = elapsed % 60
        
          println(s"Duration: $mins m $secs s")
          println(s"BldServer return code: $rc")

          sys.exit(rc)
        }
        catch {
          case ex: BldServerException => {
            Console.err.println(ex.getMessage)
            
            if (ex.getCause != null) {
              val c: Throwable = ex.getCause
              Console.err.println(c.getMessage)
            }
            
            sys.exit(1)
          }
        }

      case None =>
        // Arguments are bad, error message will have been displayed
        // VmUtilities.Failure
        sys.exit(1)
    }
  }
}

class BldServer(val config: BldServerConfig) {

  // Initialize location of logs for each build step. Not all steps have 
  // logs - only include the ones that do.
  private val mBuildLogs: Map[String, String] = Map(
      BuildStep.JAM_EXPORTS.toString     -> "jam",
      BuildStep.BLD_DATABASE.toString    -> "BldDatabase.log",
      BuildStep.VSS_BUILD_CLEAN.toString -> "vss_build_clean.log",
      BuildStep.VSS_BUILD.toString       -> "vss_build_exports.log",
      BuildStep.BLD_RUNTIME.toString     -> "BldRuntime.log",
      BuildStep.MOVE_DIRS.toString       -> "BldSetupDir.log",
      BuildStep.DATA_LOADER.toString     -> "DataLoader.log",
      BuildStep.JUNIT.toString           -> "JUnit.log",
      BuildStep.FINDBUGS.toString        -> "FindBugs.log")

  private val mPackageLogs: Map[String, String] = Map(
      PackageStep.VSS_BUILD_RELEASE.toString -> "vss_build_release.log",
      PackageStep.JAM_RELEASE.toString       -> "jam_release.log",
      PackageStep.PKG_ALL.toString           -> "pkg_all.log",
      PackageStep.PKG_VM.toString            -> "package_vm.log",
      PackageStep.PKG_APPLIANCE.toString     -> "pkg_appliance.log",
      PackageStep.PKG_SOI.toString           -> "pkg_soi.log",
      PackageStep.PKG_SYSMON.toString        -> "pkg_sysmon.log")

  private val mTOP: Option[String] = sys.env.get("TOP")
  private val mCDSTOP: Option[String] = sys.env.get("CDS_TOP")
  private val mOS: Option[String] = sys.env.get("OS")
  private val mView: Option[String] = sys.env.get("VIEW")
  private val mServiceSuiteVersion: Option[String] = sys.env.get("SERVICE_SUITE_VERSION")
  private var mBuildTag: Option[String] = sys.env.get("BUILD_TAG")

  private val mDataLoaderTemplate: String = config.dataLoaderTemplate
  private val mUserName: String = System.getProperty("user.name")
  private val mRunBuildClean: Boolean = config.clean
  private val mDropODBUser: Boolean = config.noDrop
  private val mLogPath: String = config.logPath
  private val mPackageServer: String = config.packageServer
  private val mPackagePath: String = config.packagePath

  // These may be altered depending upon other settings
  private var mODBUser: Option[String] = config.user
  private var mODBPassword: Option[String] = config.password
  private var mODBInstance: Option[String] = config.instance
  private var mID: Option[String] = None
  private var mRunSetup: Boolean = false
  private var mRunBldRuntime: Boolean = false
  private var mRunDataLoader: Boolean = false
  private var mRunJUnit: Boolean = config.junit
  private var mRunFindBugs: Boolean = config.findBugs
  private var mRunBuild: Boolean = false
  // private var mRunPackage: Boolean = config.pkg
  private var mRunPackage: Boolean = true
  private var mRunPackageVM: Boolean = config.packageVm
  private var mProfile: java.util.Properties = new java.util.Properties
  private var mHostName: String = ""
  private var mLdapPort: String = ""
  private var mIsClearCase: Boolean = true
  private var mCreateODBUser: Boolean = false
  private var mRunBuildODB: Boolean = false
  private var mIsLegacyServer: Boolean = false

  private var mBuildStep: BuildStep = BuildStep.NONE
  private var mFailedBuildStep: BuildStep = BuildStep.NONE
  private var mPackageStep: PackageStep = PackageStep.NONE
  private var mFailedPackageStep: PackageStep = PackageStep.NONE

  // Process related variables
  private var mPB: ProcessBuilder = null
  private var mProc: Process = null
  private var mLdapProc: Process = null
  private var mInputReader: StreamReader = null
  private var mErrorReader: StreamReader = null

  if (mTOP == None) throw BldServerException("TOP not set!")
  if (mCDSTOP == None) throw BldServerException("CDS_TOP not set!")
  if (mOS == None) throw BldServerException("OS not set!")

  // Determine if we are running in ClearCase or git
  val gitDir: File = new File(mTOP.get + "/../../.git")

  if (gitDir.exists && gitDir.isDirectory) {
    println("Building for Git")
    mIsClearCase = false
  }
  else {
    println("Building for Clearcase")
  }

  if (mIsClearCase && mView == None) throw BldServerException("VIEW not set!")
  if (mServiceSuiteVersion == None) throw BldServerException("SERVICE_SUITE_VERSION not set!")

  println(s"Service Suite Version ${mServiceSuiteVersion.get}")

  if (mServiceSuiteVersion.get.indexOf("91") != -1) mIsLegacyServer = true

  // Get Jenkins Build Tag, if any. Default value will be the view name
  if (mBuildTag == None) {
    if (mIsClearCase) {
      mBuildTag = mView
    }
    else {
      val sdf: java.text.SimpleDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
      mBuildTag = Some(sdf.format(new java.util.Date))
    }

    println(s"BuildID = ${mBuildTag.get}")
  }

  try {
    val addr: InetAddress = InetAddress.getLocalHost
    mHostName = addr.getHostName
  }
  catch {
    case ex: java.net.UnknownHostException => {
      throw BldServerException("BldServer: ", ex)
    }
  }

  // First argument to check for is if the -profile argument is set. If so, 
  // then process the profile properties file and ignore the rest of the
  // arguments
  if (config.profileName.isDefined) readProfile(config.profileName.get)

  if (config.uid.isDefined) {
    val portOffset = config.uid match {
      case a if 0 to 9 contains a => s"00$a"
      case b if 10 to 99 contains b => s"0$b"
      case _ => s"{config.uid}"
    }
    mID = Some(portOffset)

    println(s"Port offset: ${mID.get}")
  }

  if (config.buildOdb) {
    mRunBuild = true
    mRunBuildODB = true
  }

  if (config.setup) {
    mRunSetup = true
    mRunBldRuntime = true
    mRunDataLoader = true
  }

  if (config.setupBldRuntime) {
    mRunSetup = true
    mRunBldRuntime = true
  }

  if (config.setupDataLoader) {
    mRunSetup = true
    mRunDataLoader = true
  }

  // Short-cut parameter to specify a complete clean code + ODB build, 
  // server setup, and JUnit run.
  if (config.buildJunit) {
    mRunBuild = true
    mRunBuildODB = true
    mRunSetup = true
    mRunBldRuntime = true
    mRunDataLoader = true
    mRunJUnit = true
    mRunFindBugs = true
  }

  if (!mRunBuild && !mRunSetup && !mRunPackage && !mRunJUnit && !mRunFindBugs) {
    throw BldServerException("Missing -build, -buildodb, -setup, -setupbldruntime, -setupdataloader -buildjunit, -junit, -findbugs or -package parameter!")
  }

  println(s"Log directory = ${mLogPath}")
  println(s"Packaging directory = ${mPackagePath}")
  println(s"Packaging server = ${mPackageServer}")
  println(s"TOP = ${mTOP.get}")
  println("*** Options selected ***")

  if (mRunBuildClean) println("  * Clean before compile")
  if (mRunBuild) println("  * Compile code")
  if (mRunBuildODB) println("  * Build ODB")
  if (mRunSetup) {
    if (mRunBldRuntime) println("  * Run BldRuntime")
    
    if (mRunDataLoader) println(s"  * Run DataLoader using template $mDataLoaderTemplate")
  }
  if (mRunJUnit) println("  * Run JUnit")
  if (mRunFindBugs) println("  * Run FindBugs")
  if (mRunPackage) {
    if (mRunPackageVM) println("  * Packaging to VM")
    else println("  * Packaging to DVD")
  }
  if (mRunSetup || mRunJUnit || mRunPackage) {
    if (mODBUser != None && mODBPassword != None && mODBInstance != None) {
      val dbConn: String = s"${mODBUser.get}/${mODBPassword.get}@${mODBInstance.get}"
      
      println(s"  * Database: $dbConn")
    }
    else if (mODBInstance != None) {
      println(s"  * User creation on database: $mODBInstance")
      mCreateODBUser = true
    }
    else {
      throw BldServerException("Missing -user, -pass, or -instance argument!")
    }
  }
  if (config.buildRuntime.isDefined) println(s"  * BldRuntime: ${config.buildRuntime}")
  if (mDropODBUser) println("  * Not dropping ODB user")

  // class BldServerException extends Exception {
  //   private[bolt] def this(msg: String) {
  //     this()
  //     `super`(msg)
  //   }

  //   private[bolt] def this(msg: String, t: Throwable) {
  //     this()
  //     `super`(msg, t)
  //   }
  // }

  def convertStreamToString(is: InputStream): String = {
    try {
      new Scanner(is).useDelimiter("\\A").next
    }
    catch {
      case e: NoSuchElementException => ""
    }
  }

  private[bolt] class StreamReader(is: InputStream=null, st: String=null, os: OutputStream=null) extends Thread {
    // private[bolt] var is: InputStream = null
    // private[bolt] var st: String = null
    // private[bolt] var os: OutputStream = null

    private[bolt] def this(is: InputStream, st: String) {
      // this()
      this(is, st, null)
    }

    // private[bolt] def this(is: InputStream, st: String, redirect: OutputStream) {
    //   this()
    //   this.is = is
    //   this.st = st
    //   this.os = redirect
    // }

    override def run {
      var pw: java.io.PrintWriter = null

      try {
        if (os != null) pw = new PrintWriter(os, true)
        val isr: java.io.InputStreamReader = new java.io.InputStreamReader(is)
        val br: java.io.BufferedReader = new java.io.BufferedReader(isr)
        var line: String = null

        while ((({ line = br.readLine; line })) != null) {
          if (pw != null) pw.println(line)
        }
      }
      catch {
        // Ignore the stream closed and Bad file number messages - these are
        // reported when StopLdap is called.
        case ex: java.io.IOException => {
          if (!(ex.getMessage == "Bad file number") && !(ex.getMessage == "Stream closed")) {
            Console.err.println("Caught IOException: " + ex.getMessage)
          }
        }
      } finally {
        if (pw != null) pw.flush
      }
    }
  }

  private def deleteDirectory(dir: String) {
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("rm", "-rf", dir)
      mPB.directory(new File(mTOP.get))
      mProc = mPB.start
      mProc.waitFor
      rc = mProc.exitValue
    
      if (rc != 0) Console.err.println(s"Failed to delete directory $dir")
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println(s"deleteDirectory: Caught IO Exception: ${ex.getMessage}")
      }
      case ex: InterruptedException => {
        Console.err.println(s"deleteDirectory: Caught Interrupted Exception: ${ex.getMessage}")
      }
    }
  }

  private def deleteLog(logname: String) {
    val log: File = new File(logname)

    if (log.exists) {
      println(s"Deleting log ${logname}")
      log.delete
    }
  }

  @throws(classOf[BldServerException])
  private def readProfile(profileName: String) {
    try {
      mProfile.load(new java.io.FileInputStream(profileName))
      println(s"Read profile $profileName")
    }
    catch {
      case ex: java.io.FileNotFoundException => {
        throw BldServerException("readProfile:", ex)
      }
      case ex: java.io.IOException => {
        throw BldServerException("readProfile:", ex)
      }
    }
  }

  private def setBuildStep(newStep: BuildStep, description: String) {
    mBuildStep = newStep
    println(s"\n*** Build Step ${newStep.id} - $description ***")
  }

  private def setPackageStep(newStep: PackageStep, description: String) {
    mPackageStep = newStep
    println(s"\n*** Package Step ${newStep.id}  - $description ***")
  }

  private def getId: Option[String] = {
    setBuildStep(BuildStep.GET_ID, "Getting ID")

    if (mID != None) return mID

    var id: String = null
 
    try {
      println("here4")
      mPB = new ProcessBuilder("id", "-u")
      mPB.directory(new File(mTOP.get))
 
      println("Obtaining id")
 
      mProc = mPB.start
      
      val input: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getInputStream))
      val err: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getErrorStream))
      
      println("Waiting for id")
      
      mProc.waitFor
      
      val rc: Int = mProc.exitValue
      
      println(s"Exit value ${rc}")
      
      // id = input.readLine
      // println(s"here1: $id")
      id = "303" 

      id match {
        case null => 
          println("Failed to retrieve id")
          None
        case id => 
          println(s"id = $id")
          if (id.length != 3) throw BldServerException(s"uid ($id) must be 3 digits.")
          else mLdapPort = s"17${id}"
          println(s"LdapPort = ${mLdapPort}")

          Some(id)
      }

    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println(s"Caught IO Exception: ${ex.getMessage}")
        Some(id)
      }
      case ex: InterruptedException => {
        Console.err.println(s"Caught Interrupted Exception: ${ex.getMessage}")
        Some(id)
      }
    } finally {
      Some(id)
    }
  }

  // This function is not really needed anymore.
  // When Bolt builds an ODB from source, the rules file will automatically 
  // clean the ODB before running the real SQL scripts.
  private def createODBUser: Int = {
    setBuildStep(BuildStep.CREATE_ODB_USER, "Creating ODB User")
    
    var rc: Int = 0
    var conn: Connection = null
    
    try {
      val connectStr: String = getConnectString(mODBInstance.get)
      val driver: String = "oracle.jdbc.OracleDriver"
    
      Class.forName(driver)
    
      val url: String = "jdbc:oracle:thin:@" + connectStr
    
      conn = DriverManager.getConnection(url, "wfm_admin", "wfm_admin")
    
      val st: Statement = conn.createStatement
    
      println("Connected to DB")
      
      // generate random number for user ID and password. We'll constrain the 
      // result set to 10 digits. This algorithm was borrowed from 
      // http://stackoverflow.com/questions/363681/java-generating-random-number-in-a-range
      val max: Double = Math.pow(2, 31) - 1
      val min: Double = 1000000000
      val r: Double = min + (Math.random * (max - min))
      val id: Long = r.round
      
      mODBUser = Some(s"auto_${id}_ODB")
      mODBPassword = mODBUser
      
      println(s"ODB user: ${mODBUser.get}")
      println(s"ODB password: ${mODBPassword.get}")
      
      // Now, get wfm_admin to create the user for us. Note that we are not 
      // checking for the results here since there is no result set returned
      // by the statement.
      rc = st.executeUpdate(s"create user ${mODBUser.get} identified by ${mODBPassword.get}")
      
      println("Created user.")
      
      rc = st.executeUpdate("begin dropper.recreate_user('" + mODBUser.get.toUpperCase + "', 'ODB_DATA', 'ODB_INDEX', TRUE ); end;")
      
      println("Recreating user")
    }
    catch {
      case e: ClassNotFoundException => {
        e.printStackTrace
        Console.err.println("ClassNotFoundException: " + e.getMessage)
        return 1
      }
      case ex: SQLException => {
        Console.err.println("SQLException: " + ex.getMessage)
        return 1
      }
    } finally {
      if (conn != null) {
        System.out.println("Closing DB connection")
        try {
          conn.close
        }
        catch {
          case ex: SQLException => {
            Console.err.println("createODBUser: " + ex.getMessage)
          }
        }
      }
    }
    return 0
  }

  private def checkODB: Int = {
    setBuildStep(BuildStep.CHECK_ODB_USER, "Checking ODB connectivity..")
    
    var rc: Int = 0
    var conn: Connection = null
    
    try {
      val connectStr: String = getConnectString(mODBInstance.get)
      val driver: String = "oracle.jdbc.OracleDriver"
    
      Class.forName(driver)
    
      val url: String = "jdbc:oracle:thin:@" + connectStr
    
      conn = DriverManager.getConnection(url, "wfm_admin", "wfm_admin")
    
      val st: Statement = conn.createStatement
    
      println("Connected to DB")
      
      var rs: ResultSet = st.executeQuery(s"select null from dba_users where username = upper('${mODBUser.get}')")
      
      if (!rs.next) {
        println(s"ODB user ${mODBUser.get} does not exist in database ${mODBInstance.get}")
        rc = 1
        return rc
      }

      // Check if we have any open sessions
      rs = st.executeQuery(s"select null from v$$session where username = upper('${mODBUser.get}') and status != 'KILLED'")
      
      if (rs.next) {
        println(s"ODB user ${mODBUser.get} still has open connections to database ${mODBInstance.get}")
        rc = 1
        return rc
      }
      
      // Can we log into the ODB account
      try {
        conn = DriverManager.getConnection(url, mODBUser.get, mODBPassword.get)
      }
      catch {
        case ex: SQLException => {
          Console.err.println(s"Failed to login to ODB as ${mODBUser.get}/${mODBPassword.get}")
          rc = 1
        }
      }
      
      if (rc == 0) println("ODB user validated.")

      rc
    }
    catch {
      case ex: SQLException => {
        Console.err.println(s"Caught SQLException: ${ex.getMessage}")
        rc = 1
        rc
      }
    } finally {
      if (conn != null) {
        try {
          conn.close
        }
        catch {
          case ex: SQLException => {
            Console.err.println("Failed to close ODB connection.")
          }
        }
      }
      
      rc
    }
  }

  private def jamExports: Int = {
    setBuildStep(BuildStep.JAM_EXPORTS, "Running jam")
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("jam", "exports")
      mPB.directory(new File(mTOP.get + "/Bld/src"))
    
      println("Starting jam exports from Bld/src")
    
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)
      
      name match {
        case Some(name) =>
          var logname: String = s"${mLogPath}/${name}_exports.log"
          
          deleteLog(logname)
          
          var instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          var errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for jam...")
          
          mProc.waitFor
          
          rc = mProc.exitValue
          
          println(s"jam returned $rc")
          
          if (rc != 0) return rc

          mPB = new ProcessBuilder("jam", "idls", "headers")
          mPB.directory(new File(mTOP.get))
          
          println("Starting jam idls headers from TOP")
          
          mProc = mPB.start
          logname = s"${mLogPath}/${name}_headers.log"
          deleteLog(logname)
          
          instr = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          errstr = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for jam...")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Jam returned $rc")
          
          if (rc != 0) return rc
          
          mPB = new ProcessBuilder("jam", "exports")
          mPB.directory(new File(mTOP.get))
          
          println("Starting jam exports from TOP")
          
          mProc = mPB.start
          logname = s"{mLogPath}/${name}_exports_from_TOP.log"
          deleteLog(logname)
          
          instr = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          errstr = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for jam exports")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Jam returned $rc")
          
          // if (rc != 0) return rc  original line
          rc

        case None =>
          println(s"Missing log file name for step ${mBuildStep}")
          1
      }      
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -100
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception: " + ex.getMessage)
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      rc
    }
  }

  private def bldDatabase: Int = {
    setBuildStep(BuildStep.BLD_DATABASE, "Running BldDatabase")
    
    var rc: Int = 0
    
    try {
      val conn: String = s"${mODBUser.get}/${mODBPassword.get}@${mODBInstance.get}"
      val args: java.util.ArrayList[String] = new java.util.ArrayList[String]
    
      args.add("perl")
      args.add("BldDatabase.pl")
      args.add("-odb")
      args.add(conn)
      args.add("-rules")
      args.add("r9_db_from_source.rules")
      args.add("-otablespace")
      args.add("odb_data")
      args.add("-oindexspace")
      args.add("odb_index")
    
      var i: Int = 1
      
      import scala.collection.JavaConversions._
    
      for (a <- args) {
        println(s"Arg $i : $a")
        i += 1
      }
    
      mPB = new ProcessBuilder(args)
      
      val toolsDir: String = mTOP.get + "/Bld/src"
      
      mPB.directory(new File(toolsDir))
      
      println("Starting BldDatabase")
      
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = s"${mLogPath}/${name}"
          deleteLog(logname)
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for BldDatabase")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")

          rc

        case None =>
          println(s"Missing log file name for step ${mBuildStep}")
          1
      }      
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -101
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception: " + ex.getMessage)
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      rc
    }
  }

  private def vssbuildclean: Int = {
    setBuildStep(BuildStep.VSS_BUILD_CLEAN, "Running vss_build clean")
    
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("vss_build", "clean", "-logpath", mLogPath)
      mPB.directory(new File(mTOP.get))
    
      println("Starting vss_build clean")
      
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)

      name match {
        case Some(name) =>
          val logname: String = s"${mLogPath}/${name}"
          deleteLog(logname)
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for vss_build clean")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")

          rc

        case None =>
          println(s"Missing log file name for step ${mBuildStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -102
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      rc
    }
  }

  private def vssbuild: Int = {
    setBuildStep(BuildStep.VSS_BUILD, "Running vss_build")
    
    var db: Boolean = false
    var rc: Int = 0
   
    try {
      if (mODBUser != None && mODBPassword != None && mODBInstance != None && mRunBuildODB) {
        db = true
        val dbConn: String = s"${mODBUser.get}/${mODBPassword.get}@${mODBInstance.get}"
        mPB = new ProcessBuilder("vss_build", "-jam", "-j6", "-db", dbConn, "-log", "exports", "-logpath", mLogPath)
      }
      else {
        mPB = new ProcessBuilder("vss_build", "-jam", "-j6", "-log", "exports", "-logpath", mLogPath)
      }
   
      mPB.directory(new File(mTOP.get))
   
      println("Starting vss_build exports")
      
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = s"${mLogPath}/${name}"
          deleteLog(logname)
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
       
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
         
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
       
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
       
          mInputReader.start
          mErrorReader.start
         
          println("Waiting for vss_build")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None =>
          println(s"Missing log file name for step ${mBuildStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -103
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      if (db) copyDir("reports", mTOP.get + "/" + mOS.get)
      rc
    }
  }

  private def loadJavaPrivileges: Int = {
    setBuildStep(BuildStep.LOAD_PRIVILEGES, "Granting Java Privileges")
    
    var rc: Int = 0
    var conn: Connection = null
    
    try {
      val connectStr: String = getConnectString(mODBInstance.get)
      val driver: String = "oracle.jdbc.OracleDriver"
    
      Class.forName(driver)
    
      val url: String = s"jdbc:oracle:thin:@${connectStr}"
    
      conn = DriverManager.getConnection(url, "wfm_admin", "wfm_admin")
    
      val st: Statement = conn.createStatement
    
      println("Connected to DB")
      
      val stmt1 = s"begin grant_java_privilege('${mODBUser.get}', '${mHostName}', ${mLdapPort}); end;"
      val stmt2 = s"begin dbms_java.grant_permission('${mODBUser.get.toUpperCase}', 'SYS:java.security.SecurityPermission','putProviderProperty.SunJSSE', ''); end;"
      println(s"STMT1: $stmt1")
      println(s"STMT2: $stmt2")
      rc = st.executeUpdate(stmt1)
      rc = st.executeUpdate(stmt2)
      
      println("Privileges granted.")
      rc
    }
    catch {
      case e: ClassNotFoundException => {
        Console.err.println("loadJavaPrivileges: " + e.getMessage)
        1
      }
      case ex: SQLException => {
        Console.err.println("loadJavaPrivileges: " + ex.getMessage)
        1
      }
    } finally {
      if (conn != null) {
        System.out.println("Closing DB connection")
        try {
          conn.close
        }
        catch {
          case ex: SQLException => {
            Console.err.println("createODBUser: " + ex.getMessage)
          }
        }
      }
    }
    
    0
  }

  private def bldRuntime(user: String, password: String, odbinstance: String): Int = {
    setBuildStep(BuildStep.BLD_RUNTIME, "Running BldBoltRuntime")

    // Ensure that LDAP is fully shut-down before running BldRuntime. This will
    // cause failures if LDAP and SVN are still running.
    stopLdapHelper
    
    var rc: Int = 0
    
    try {
      // Create the temp directory if it does not exist. Required for LDAP build.
      val tdir: File = new File(mTOP.get + "/temp")
    
      if (!tdir.exists) {
        if (!tdir.mkdir) {
          Console.err.println("Failed to make TOP/temp directory")
          rc = 1
          return rc
        }
      }
    
      println("Starting BldBoltRuntime")
      
      val wlsDir: String = System.getenv("WLS_DIR")
      
      if (wlsDir == null || wlsDir.isEmpty) {
        Console.err.println("WLS_DIR not set!")
        return 1
      }
      
      val cdsTop: String = System.getenv("CDS_TOP")
      
      if (cdsTop == null || cdsTop.isEmpty) {
        Console.err.println("CDS_TOP not set!")
        return 1
      }
      
      val args: java.util.ArrayList[String] = new java.util.ArrayList[String]
      
      args.add("BldBoltRuntime")
      args.add("-verbose")
      args.add("act=" + user)
      args.add("pswd=" + password)
      args.add("oracle=" + odbinstance)
      args.add("wls_dir=" + wlsDir)
      args.add("cds_top=" + cdsTop)
      
      if (mID != None) args.add(s"id=${mID.get}")

      val tmp: Option[String] = config.buildRuntime
      
      if (tmp != None) {
        var temp = tmp.get
        val start: Int = temp.indexOf('"')
        val end: Int = temp.lastIndexOf('"')
      
        if (start != -1 && end != -1 && (start + 1) != end) {
          temp = temp.substring(start + 1, end)
        }
      
        val bldRuntimeArgs: Array[String] = temp.split(" ")
        for (b <- bldRuntimeArgs) {
          args.add(b)
        }
      }
      
      var i: Int = 1
      
      import scala.collection.JavaConversions._
      
      for (a <- args) {
        System.out.println("Arg " + i + ": " + a)
        i += 1
      }
      
      mPB = new ProcessBuilder(args)
      
      val toolsDir: String = s"${mTOP.get}/${mOS.get}/tools"
      mPB.directory(new File(toolsDir))
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString) 
      
      name match{
        case Some(name) =>
          val logname: String = s"${mLogPath}/${name}"
          deleteLog(logname)
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for BldBoltRuntime")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None =>
          println(s"Missing log file name for step ${mBuildStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -104
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception: ")
        rc = 1
        mProc.destroy
        rc
      }
      case ex: Exception => {
        Console.err.println("Caught Unhandled Exception: " + ex.getMessage)
        ex.printStackTrace
        rc = -105
        rc
      }
    } finally {
      rc
    }
  }

  private def moveDirectories: Int = {
    setBuildStep(BuildStep.MOVE_DIRS, "Moving Directories")
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("BldSetupDir")
      mPB.directory(new File(s"${mTOP.get}/${mOS.get}/tools"))
    
      println("Moving bin, etc, logs directories...")
      
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = mLogPath + "/" + name
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for BldSetupDir")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None =>
          println(s"Missing log file name for step ${mBuildStep}")
          1
      }
      
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -106
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        rc
      }
    } finally {
      rc
    }
  }

  private def getConnectString(dbStr: String): String = {
    var retval: String = null
    
    try {
      val p: Process = Runtime.getRuntime.exec("tnsping " + dbStr)
      val input: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream))
      var line: String = null

      while ((({ line = input.readLine; line })) != null) {
        if (line.startsWith("Attempting to contact (")) {
          retval = line.substring(line.indexOf("("))
        }
      }
      input.close
    }
    catch {
      case ex: java.io.IOException => {
        println("IOException connecting to database: " + ex)
      }
    }
    
    retval
  }

  private def loadCompose: Int = {
    setBuildStep(BuildStep.LOAD_COMPOSE, "Running fake Compose deployment")
    
    val filename: String = s"${mTOP.get}/Dl/src/DataLoader/dl_compose.sql"
    var dlCompose: String = new String
    
    try {
      val input: BufferedReader = new BufferedReader(new java.io.FileReader(filename))
      var line: String = null
    
      while ((({ line = input.readLine; line })) != null) {
        if (!line.startsWith("/")) {
          dlCompose += line
          dlCompose += "\n"
        }
      }
      
      input.close
    }
    catch {
      case e: java.io.IOException => {
        Console.err.println("loadCompose: " + e.getMessage)
        return 1
      }
    }

    var conn: Connection = null
    var rc: Int = 0
   
    try {
      val connectStr: String = getConnectString(mODBInstance.get)
      val driver: String = "oracle.jdbc.OracleDriver"
   
      Class.forName(driver)
   
      val url: String = "jdbc:oracle:thin:@" + connectStr
   
      conn = DriverManager.getConnection(url, mODBUser.get, mODBPassword.get)
   
      val st: Statement = conn.createStatement
   
      println("Connected to DB")
      println(s"Running ${filename}")
      
      rc = st.executeUpdate(dlCompose)
      
      println("Finished")
    }
    catch {
      case e: ClassNotFoundException => {
        Console.err.println("ClassNotFoundException: " + e.getMessage)
        return 1
      }
      case ex: SQLException => {
        Console.err.println("loadCompose: " + ex.getMessage)
        return 1
      }
    } finally {
      if (conn != null) {
        System.out.println("Closing DB connection")
        try {
          conn.close
        }
        catch {
          case ex: SQLException => {
            Console.err.println("loadCompose: " + ex.getMessage)
          }
        }
      }
    }
    
    0
  }

  private def startLdap: Int = {
    setBuildStep(BuildStep.START_LDAP, "Starting LDAP")
    
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("StartLdap")
      mPB.directory(new File(s"${mTOP.get}/${mOS.get}/bin"))
    
      println("Starting LDAP")
      
      mLdapProc = mPB.start
      
      val input: BufferedReader = new BufferedReader(new InputStreamReader(mLdapProc.getInputStream))
      val err: BufferedReader = new BufferedReader(new InputStreamReader(mLdapProc.getErrorStream))
      
      // Wait for LDAP to fully start before continuing
      Thread.sleep(5000)

      rc
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -107

        rc
      }
    } finally {
      return rc
    }
  }

  private def killProcess(pid: Int) {
    try {
      
      val pb: ProcessBuilder = new ProcessBuilder("kill", String.valueOf(pid))
      
      pb.directory(new File(s"${mTOP.get}/${mOS.get}/bin"))
      
      val proc: Process = pb.start
      val input: BufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream))
      val err: BufferedReader = new BufferedReader(new InputStreamReader(proc.getErrorStream))
      
      proc.waitFor
      Thread.sleep(5000)
      proc.destroy
    }
    catch {
      case ex: InterruptedException => {
        Console.err.println("Caught InterruptedException: " + ex.getMessage)
      }
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
      }
    }
  }

  private def stopSlapd {
    try {
      val cmd: String = "ps -fu " + mUserName + " | grep slapd | grep -v grep | awk '{print \\$2}'"
      
      mPB = new ProcessBuilder("/bin/ksh", "-c", cmd)
      
      println("Stopping slapd...")
      
      mProc = mPB.start
      
      val input: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getInputStream))
      val err: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getErrorStream))
      
      mProc.waitFor
      
      var pid: Int = -1
      var line: String = null
      
      while ((({ line = input.readLine; line })) != null) {
        val pidstr: String = line.trim
        
        try {
          pid = pidstr.toInt
        }
        catch {
          case ex: NumberFormatException => {
            Console.err.println("Not a valid PID: " + pidstr)
            // continue //TODO: continue is not supported
          }
        }
        
        println("Stopping slapd, pid = " + pid)
        killProcess(pid)
      }
      input.close
    }
    catch {
      case ex: InterruptedException => {
        Console.err.println("Caught InterruptedException: " + ex.getMessage)
      }
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
      }
    }
  }

  private def stopSvn {
    try {
      mPB = new ProcessBuilder("StopSvn")
      mPB.directory(new File(s"${mTOP.get}/${mOS.get}/bin"))
      
      println("Stopping SVN...")
      
      mProc = mPB.start
      
      val input: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getInputStream))
      val err: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getErrorStream))
      
      mProc.waitFor
    }
    catch {
      case ex: InterruptedException => {
        Console.err.println("Caught InterruptedException: " + ex.getMessage)
      }
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
      }
    }
  }

  /**
   * Finds the LDAP processes and stops them
   * 
   * @return 0 for success, non-zero otherwise
   */
  private def stopLdapHelper: Int = {
    var rc: Int = 0
    var found: Boolean = false
    
    // Try a graceful shut-down first, as this will shut-down the LDAP and SVN processes
    try {
      mPB = new ProcessBuilder("ps", "-fu", mUserName)
      mPB.directory(new File(s"${mTOP.get}/${mOS.get}/bin"))
    
      println("Getting StartLdap PID...")
      
      mProc = mPB.start
      val input: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getInputStream))
      val err: BufferedReader = new BufferedReader(new InputStreamReader(mProc.getErrorStream))
      
      mProc.waitFor
      
      var pid: Int = -1
      var line: String = null
    
      // The process list needs to be scanned for the string StartLdap.
      // While not fool proof, it will suffice for internal use.  
      while ((({ line = input.readLine; line })) != null) {
        line = line.trim
      
        if (line.contains(" StartLdap") || line.contains(" slapd")) {
          val splitLine: Array[String] = line.split("\\s+")
          // PID is the second argument after the fist space
          val pidstr: String = splitLine(1)
          
          try {
            pid = pidstr.toInt
          }
          catch {
            case ex: NumberFormatException => {
              Console.err.println("Not a valid PID: " + pidstr)
              // continue //TODO: continue is not supported
            }
          }

          found = true
          System.out.println("Stopping pid " + pid)
          killProcess(pid)
        }
      }
      
      input.close

      rc
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println(s"Caught IO Exception: ${ex.getMessage}")
        rc = -108
        rc
      }
    } finally {
      // StartLdap wasn't found, then shut-down any old SLAPD and SVN processes
      if (!found) {
        stopSlapd
        stopSvn
      }

      // Ungraceful shut-down of the process. Hopefully, LDAP is already 
      // shut-down from the previous step. If not, there is a possibility
      // the process will still linger.
      if (mLdapProc != null) {
        mLdapProc.destroy
        mLdapProc = null
      }
      return rc
    }
  }

  private def stopLdap: Int = {
    setBuildStep(BuildStep.STOP_LDAP, "Stopping LDAP")
    return stopLdapHelper
  }

  private def dataLoader: Int = {
    setBuildStep(BuildStep.DATA_LOADER, "Running Dataloader")
    
    var rc: Int = 0

    try {
      // TODO: Provide an option to pass in the type of Template. Right now, it
      // has been hard-coded to use AutotestTemplate since this is what JUnit uses.
      mPB = new ProcessBuilder("java", "-jar", "Dataloader.jar", "-template", mTOP.get + "/../../SYSTEM/Support.WFM/DataLoader/" + mDataLoaderTemplate, "-dataLoaderXLS", mTOP + "/../../SYSTEM/Support.WFM/DataLoader/DataLoader_Base.xls", "-db", mODBUser.get + "/" + mODBPassword.get + "@" + mODBInstance.get)
      mPB.directory(new File(mTOP.get + "/Dl/src/DataLoader/dist"))

      println("Starting Dataloader")
      
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = mLogPath + "/" + name
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }

          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for Dataloader")
          
          mProc.waitFor
          rc = mProc.exitValue
          println(s"Exit value ${rc}")

          rc

        case None =>
          println(s"Missing log file name for step ${mBuildStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("dataLoader: IO exception: " + ex.getMessage)
        rc = -109
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught InterruptedException ")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      rc
    }
  }

  private def postDataLoader: Int = {
    setBuildStep(BuildStep.POST_DATA_LOADER, "Running post DataLoader script")
    
    val filename: String = mTOP.get + "/Dl/src/DataLoader/postDataLoaderPreJunit.sql"
        val f: File = new File(filename)
    
    if (!f.exists) {
      println(s"No $filename . Skipping step...")
      return 0
    }
    
    var postdl: String = new String
    
    try {
      val input: BufferedReader = new BufferedReader(new java.io.FileReader(filename))
      var line: String = null
    
      while ((({ line = input.readLine; line })) != null) {
        if (!line.startsWith("/")) {
          postdl += line
          postdl += "\n"
        }
      }
      
      input.close
    }
    catch {
      case e: java.io.IOException => {
        Console.err.println("postDataLoader: " + e.getMessage)
        return 1
      }
    }
    
    var conn: Connection = null
    
    try {
      val connectStr: String = getConnectString(mODBInstance.get)
      val driver: String = "oracle.jdbc.OracleDriver"
    
      Class.forName(driver)
    
      val url: String = "jdbc:oracle:thin:@" + connectStr
    
      conn = DriverManager.getConnection(url, mODBUser.get, mODBPassword.get)
    
      val st: Statement = conn.createStatement
    
      println("Connected to DB")
      println(s"Running ${filename}")
      
      st.executeUpdate(postdl)
      
      println("Finished")
    }
    catch {
      case e: ClassNotFoundException => {
        Console.err.println("ClassNotFoundException: " + e.getMessage)
        return 1
      }
      case ex: SQLException => {
        Console.err.println("postDataLoader: " + ex.getMessage)
        return 1
      }
    } finally {
      if (conn != null) {
        System.out.println("Closing DB connection")
        try {
          conn.close
        }
        catch {
          case ex: SQLException => {
            Console.err.println("postDataLoader: " + ex.getMessage)
          }
        }
      }
    }
    return 0
  }

  private def systemID: Int = {
    setBuildStep(BuildStep.SYSTEM_ID, "Loading SystemID")
    
    println(s"Setting SystemID to ${mUserName}")
    
    var conn: Connection = null
    
    try {
      val connectStr: String = getConnectString(mODBInstance.get)
      val driver: String = "oracle.jdbc.OracleDriver"
    
      Class.forName(driver)
    
      val url: String = s"jdbc:oracle:thin:@${connectStr}"
    
      conn = DriverManager.getConnection(url, mODBUser.get, mODBPassword.get)
    
      val st: Statement = conn.createStatement
    
      println("Connected to DB")
      
      val stmt: String = s"begin meta_reg.upsert_entry( '/System/Advantex/SystemID','text','${mUserName}'); end;"
      println(s"Update registry with: ${stmt}")
      val rc: Int = st.executeUpdate(stmt)
      
      if (rc != 1) {
        Console.err.println("Failed to set system ID!")
        return 1
      }
    }
    catch {
      case e: ClassNotFoundException => {
        Console.err.println("systemID: " + e.getMessage)
        return 1
      }
      case ex: SQLException => {
        Console.err.println("systemID: " + ex.getMessage)
        return 1
      }
    } finally {
      if (conn != null) {
        System.out.println("Closing DB connection")
        try {
          conn.close
        }
        catch {
          case ex: SQLException => {
            Console.err.println("createODBUser: " + ex.getMessage)
          }
        }
      }
    }
    return 0
  }

  private def jUnit: Int = {
    setBuildStep(BuildStep.JUNIT, "Running Server JUnit")

    var rc: Int = 0

    try {
      mPB = new ProcessBuilder("perl", "BldWlsTest.pl", "-junit")
      mPB.directory(new File(mTOP.get + "/weblogic/tools"))
      
      println("Starting junit")
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)
      
      if (name == null) {
        println(s"Missing log file name for step $mBuildStep")
        return 1
      }

      val logname: String = mLogPath + "/" + name
      val log: File = new File(logname)
      
      if (log.exists) {
        println("Deleting old log")
        log.delete
      }

      val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
      
      mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
      
      val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
      
      mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
      mInputReader.start
      mErrorReader.start
      
      println("Waiting for junit")
      
      mProc.waitFor
      rc = mProc.exitValue
      
      val report: String = new Scanner(new File(logname), "UTF-8").useDelimiter("\\A").next
      var p: java.util.regex.Pattern = java.util.regex.Pattern.compile("BUILD FAILED")
      var m: java.util.regex.Matcher = p.matcher(report)
      var fail: Boolean = m.find

      if (fail) {
        println("Found JUnit failures.")
        rc = 15
      }

      if (!fail) {
        p = java.util.regex.Pattern.compile("\\[junit\\].*FAILED")
        m = p.matcher(report)
        fail = m.find
      
        if (fail) {
          println("Found JUnit failures.")
          rc = 1
        }
      }

      if (!fail) {
        p = java.util.regex.Pattern.compile("\\[javac\\]\\s+\\[^N]|Could not create task")
        m = p.matcher(report)
        fail = m.find
      
        if (fail) {
          println("Found JUnit errors.")
          rc = 1
        }
      }
      
      if (!fail) {
        println("JUnit passed!")
      }

      rc
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("JUnit: IOException: " + ex.getMessage)
        rc = 1
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("JUnit: InterruptedException ")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      // 15 means that the JUnit buiuld failed ao there is no report
      if (rc != 15) copyFile("junit-noframes.html", mTOP.get + "/Java/Test/ServicePojoTest/build/test/report", mLogPath)
      
      println("Exit value " + rc)
      rc
    }
  }

  private def findBugs: Int = {
    setBuildStep(BuildStep.FINDBUGS, "Running FindBugs")
    
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("perl", "BldWlsAnalysis.pl", "-findbugs")
      mPB.directory(new File(mTOP + "/weblogic/tools"))
    
      println("Starting FindBugs")
      
      mProc = mPB.start
      
      val name: Option[String] = mBuildLogs.get(mBuildStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = mLogPath + "/" + name
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for FindBugs")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          var report: String = new java.util.Scanner(new java.io.File(logname), "UTF-8").useDelimiter("\\A").next
          var p: java.util.regex.Pattern = java.util.regex.Pattern.compile("BUILD FAILED")
          var m: java.util.regex.Matcher = p.matcher(report)
          val fail: Boolean = m.find
          
          if (fail) {
            println("Found FindBugs failures.")
            rc = 1
            return rc
          }
          
          val r: File = new File(mTOP + "/Java/findbugs-result.xml")
          
          if (r.exists) {
            report = new Scanner(r, "UTF-8").useDelimiter("\\A").next
            p = Pattern.compile(".*total_bugs=\"0\".*")
            m = p.matcher(report)
          
            if (!m.find) {
              println("FindBugs found bugs!")
              rc = 1
              return rc
            }
          }
          else {
            println("No findbugs report found!")
            rc = 1
            return rc
          }
          
          println("FindBugs passed!")
          rc

        case None =>
          println(s"Missing log file name for step $mBuildStep")
          1
      }      
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("findBugs: IO exception: " + ex)
        rc = 1
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("findBugs: InterruptedException: ")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      copyFile("findbugs-result.xml", mTOP + "/Java", mLogPath)
      println(s"Exit value $rc")
      rc
    }
  }

  private def dropDatabase: Int = {
    mBuildStep = BuildStep.DROP_ODB_USER
    
    var conn: Connection = null
    var rc: Int = 0
    
    try {
      val connectStr: String = getConnectString(mODBInstance.get)
      val driver: String = "oracle.jdbc.OracleDriver"
      Class.forName(driver)
    
      val url: String = "jdbc:oracle:thin:@" + connectStr
    
      conn = DriverManager.getConnection(url, "wfm_admin", "wfm_admin")
    
      val st: Statement = conn.createStatement
    
      println("Dropping ODB user")
      
      // FIXME: put back the proper commands
      rc = st.executeUpdate("declare v_date varchar2(100); begin select sysdate into v_date from dual; end;")
      // rc = st.executeUpdate("begin wfm_dropper.dropper.recreate_user('" + mODBUser.toUpperCase + "', 'ODB_DATA', 'ODB_INDEX', TRUE ); end;")
      // rc = st.executeUpdate("begin execute immediate 'DROP USER " + mODBUser.toUpperCase + " CASCADE'; end;")
      
      println("User dropped.")
      rc
    }
    catch {
      case e: ClassNotFoundException => {
        Console.err.println("ClassNotFoundException: " + e.getMessage)
        1
      }
      case ex: SQLException => {
        Console.err.println("dropDatabase: " + ex.getMessage)
        1
      }
    } finally {
      if (conn != null) {
        System.out.println("Closing DB connection")
        try {
          conn.close
        }
        catch {
          case ex: SQLException => {
            Console.err.println("createODBUser: " + ex.getMessage)
          }
        }
      }
    }
    
    0
  }

  private def copyDir(sourceDir: String, workingDir: String): Int = {
    var rc: Int = 0
    
    if (mLogPath == mTOP) return rc
    
    try {
      mPB = new ProcessBuilder("cp", "-r", sourceDir, mLogPath)
      mPB.directory(new File(workingDir))
    
      println(s"Copying $sourceDir Directory")
      
      mProc = mPB.start
      
      val input: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(mProc.getInputStream))
      val err: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(mProc.getErrorStream))
      
      mProc.waitFor
      
      rc = mProc.exitValue
      rc
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = 1
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception: " + ex.getMessage)
        rc = 1
        rc
      }
    } finally {
      rc
    }
  }

  private def copyFile(filename: String, sourceDir: String, destDir: String): Int = {
    var rc: Int = 0
    
    if (mLogPath == mTOP) return rc
    
    try {
      mPB = new ProcessBuilder("cp", filename, destDir)
      mPB.directory(new File(sourceDir))
    
      println(s"Copying $filename")
      
      mProc = mPB.start
      
      val input: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(mProc.getInputStream))
      val err: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(mProc.getErrorStream))
      
      mProc.waitFor
      
      rc = mProc.exitValue
      rc
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = 1
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception ")
        rc = 1
        rc
      }
    } finally {
      rc
    }
  }

  private def getStartEnv: Int = {
    setPackageStep(PackageStep.START_ENV, "Detecting current ODB user")
    
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("StartEnv", "-verbose")
      mPB.directory(new File(s"${mTOP.get}/${mOS.get}/bin"))
    
      println("Starting StartEnv")
      mProc = mPB.start
      
      val input: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(mProc.getInputStream))
      val err: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(mProc.getErrorStream))
      
      println("Waiting for StartEnv")
      
      mProc.waitFor
      rc = mProc.exitValue
      
      println(s"Exit value $rc")
      
      var line: String = input.readLine
      
      while (line != null) {
        var i: Int = line.indexOf("dbInstance=")
      
        if (i != -1) mODBInstance = Some(line.substring(i + 11))

        i = line.indexOf("dbUser=")

        if (i != -1) mODBUser = Some(line.substring(i + 7))

        i = line.indexOf("dbPassword=")

        if (i != -1) mODBPassword = Some(line.substring(i + 11))

        line = input.readLine
      }

      rc
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -110
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception ")
        rc = 1
        rc
      }
    } finally {
      if (mODBInstance == None || mODBUser == None || mODBPassword == None) {
        println("No ODB credentials found in StartEnv.")
        rc = 1
      }

      rc
    }
  }

  private def vss_build_release: Int = {
    setPackageStep(PackageStep.VSS_BUILD_RELEASE, "Running vss_build release")

    var rc: Int = 0

    try {
      mPB = new ProcessBuilder("vss_build", "-logpath", mLogPath, "-log", "release", "release")
      mPB.directory(new File(mTOP.get))

      println("Starting vss_build release")
      
      mProc = mPB.start
      
      val name: Option[String] = mPackageLogs.get(mPackageStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = s"${mLogPath}/${name}"
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for vss_build")
          
          mProc.waitFor
          
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None=>
          println(s"Missing log file name for step ${mPackageStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -111
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      
      rc
    }
  }

  private def jamRelease: Int = {
    setPackageStep(PackageStep.JAM_RELEASE, "Running jam release")
    
    var rc: Int = 0
    
    try {
      mPB = new ProcessBuilder("jam", "release")
      mPB.directory(new File(mTOP.get))
    
      println("Starting jam release")
      
      mProc = mPB.start
      
      val name: Option[String] = mPackageLogs.get(mPackageStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = s"${mLogPath}/${name}"
      
          deleteLog(logname)
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for jam...")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println("jam returned " + rc)
          
          if (rc != 0) return rc
          rc

        case None =>
          println(s"Missing log file name for step $mPackageStep")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -112
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      
      rc
    }
  }

  private def pkgAll: Int = {
    setPackageStep(PackageStep.PKG_ALL, "Running pkg_all")
    
    var rc: Int = 0
    
    try {
      val pkgArgs: java.util.ArrayList[String] = new java.util.ArrayList[String]
      val dbString: String = s"${mODBUser.get}/${mODBPassword.get}@${mODBInstance.get}"
    
      pkgArgs.add("pkg_all")
      pkgArgs.add(dbString)
    
      if (mPackagePath != null) pkgArgs.add(s"$mPackagePath/DVD_Images")

      val tmp: Option[String] = config.pkg

      if (tmp != None) {
        var temp = tmp.get
        val start: Int = temp.indexOf('"')
        val end: Int = temp.lastIndexOf('"')
        
        if (start != -1 && end != -1 && (start + 1) != end) temp = temp.substring(start + 1, end)

        val args: Array[String] = temp.split(" ")

        for (a <- args) {
          pkgArgs.add(a)
        }
      }
      
      if (!mRunPackageVM) {
        pkgArgs.add("-noVM")
        pkgArgs.add("-noSOI")
        pkgArgs.add("-noMobile")
      }
      
      var  i: Int = 1
      
      import scala.collection.JavaConversions._
      
      for (e <- pkgArgs) {
        println(s"Arg $i : $e")
        i += 1
      }
      
      mPB = new ProcessBuilder(pkgArgs)
      mPB.directory(new File(mTOP + "/Inst/src"))
      
      println("Starting pkg_all")
      
      mProc = mPB.start
      
      val name: Option[String] = mPackageLogs.get(mPackageStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = mLogPath + "/" + name
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for pkg_all")
          
          mProc.waitFor
          
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None =>
          Console.err.println(s"Missing log file name for step ${mPackageStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -113
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      
      rc
    }
  }

  private def pkgAppliance: Int = {
    setPackageStep(PackageStep.PKG_APPLIANCE, "Running pkg_appliance")
    
    if (!(mOS == "LINUX")) {
      println(s"Skipping VM packaging for OS ${mOS.get}")
      return 0
    }
    
    var rc: Int = 0
    
    try {
      // Make the working directory first (under tyhe main packaging directory)
      val workingDir: String = mPackagePath + "/tmp"
      val dir: File = new File(workingDir)
      
      if (!dir.exists) {
        if (!dir.mkdir) {
          Console.err.println(s"Failed to make working directory $workingDir")
          return 1
        }
      }
      
      // Set directory to be writable by anyone (required for vssrt)
      dir.setWritable(true, false)

      // Copy the pkg_appliance and template scripts to the Packaging directory
      copyFile("pkg_appliance", mTOP + "/Inst/src", mPackagePath)
      copyFile("pkg_common", mTOP + "/Inst/src", mPackagePath)
      copyFile("pkg_sysmon", mTOP + "/Inst/src", mPackagePath)

      // Copy EsxTools jar file to packaging directory. We will need it later 
      // to load the .ova file into the ESX server
      copyFile("EsxTools-1.0.jar", mTOP + "/weblogic/tools", mPackagePath)

      // TODO: Copy WebConfigCli to packaging directory. We will need it later
      // to configure the VM Appliance.
      copyFile("WebConfigCli.jar", mTOP + "/weblogic/tools", mPackagePath)
      copyFile("Logging.jar", mTOP + "/weblogic/dist", mPackagePath)
      
      val pkgAppliance: java.util.ArrayList[String] = new java.util.ArrayList[String]
      
      pkgAppliance.add("ssh")
      pkgAppliance.add("-t")
      pkgAppliance.add("-t")
      pkgAppliance.add("vssrt@" + mPackageServer)
      pkgAppliance.add("export")
      pkgAppliance.add("TOP=" + mTOP.get + ";")
      pkgAppliance.add(mPackagePath + "/pkg_appliance")
      pkgAppliance.add("-p")
      pkgAppliance.add(mPackagePath + "/DVD_Images")
      pkgAppliance.add("-w")
      pkgAppliance.add(workingDir)
      pkgAppliance.add("-o")
      pkgAppliance.add(mPackagePath + "/vss_appliance.ova")
      pkgAppliance.add("-version")
      pkgAppliance.add(mBuildTag.get)
      pkgAppliance.add("-u")
      pkgAppliance.add(mUserName)
      pkgAppliance.add("-nocleanup")
      
      var i: Int = 1
      
      println("Packaging command:")
      
      import scala.collection.JavaConversions._
      
      for (e <- pkgAppliance) {
        println(s"Arg $i : $e")
        i += 1
      }
      
      mPB = new ProcessBuilder(pkgAppliance)
      mPB.directory(new File(mPackagePath))
      
      println("Starting pkg_appliance")
      
      mProc = mPB.start
      
      val name: Option[String] = mPackageLogs.get(mPackageStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = s"${mLogPath}/${name}"
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for pkg_appliance")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None =>
          Console.err.println(s"Missing log file name for step ${mPackageStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println(s"Caught IO Exception: ${ex.getMessage}")
        rc = -114
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      rc
    }
  }

  private def pkgSOI: Int = {
    setPackageStep(PackageStep.PKG_SOI, "Running pkg_soi")
    
    if (!(mOS == "LINUX")) {
      println(s"Skipping SOI VM packaging for OS ${mOS.get}")
      return 0
    }
    
    var rc: Int = 0
    
    try {
      // Make the working directory first (under the main packaging directory)
      val workingDir: String = mPackagePath + "/tmp"
      val dir: File = new File(workingDir)
    
      if (!dir.exists) {
        if (!dir.mkdir) {
          Console.err.println(s"Failed to make working directory $workingDir")
          return 1
        }
      }
      
      // Set directory to be writable by anyone (req'd for vssrt)
      dir.setWritable(true, false)

      // Copy the pkg_common and template scripts to the Packaging directory
      copyFile("pkg_soi", mTOP + "/Inst/src", mPackagePath)
      copyFile("pkg_common", mTOP + "/Inst/src", mPackagePath)
      
      val pkgSOI: java.util.ArrayList[String] = new java.util.ArrayList[String]
      
      pkgSOI.add("ssh")
      pkgSOI.add("-t")
      pkgSOI.add("-t")
      pkgSOI.add("vssrt@" + mPackageServer)
      pkgSOI.add("export")
      pkgSOI.add("TOP=" + mTOP + ";")
      pkgSOI.add(mPackagePath + "/pkg_soi")
      pkgSOI.add("-p")
      pkgSOI.add(mPackagePath + "/DVD_Images")
      pkgSOI.add("-w")
      pkgSOI.add(workingDir)
      pkgSOI.add("-o")
      pkgSOI.add(mPackagePath + "/vm_soi.ova")
      pkgSOI.add("-version")
      pkgSOI.add(mBuildTag.get)
      pkgSOI.add("-u")
      pkgSOI.add(mUserName)
      
      var i: Int = 1
      
      println("Packaging command:")
      
      import scala.collection.JavaConversions._
      
      for (e <- pkgSOI) {
        println(s"Arg $i : $e")
        i += 1
      }
      
      mPB = new ProcessBuilder(pkgSOI)
      mPB.directory(new File(mPackagePath))
      
      println("Starting pkg_soi")
      
      mProc = mPB.start
      
      val name: Option[String] = mPackageLogs.get(mPackageStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = mLogPath + "/" + name
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }

          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for pkg_soi")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None =>
          Console.err.println(s"Missing log file name for step ${mPackageStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -115
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      rc
    }
  }

  private def pkgSysMon: Int = {
    setPackageStep(PackageStep.PKG_SYSMON, "Running pkg_sysmon")
    
    if (!(mOS == "LINUX")) {
      println(s"Skipping Hyperic VM packaging for OS ${mOS.get}")
      return 0
    }

    var rc: Int = 0
    
    try {
      // Make the working directory first (under the main packaging directory)
      val workingDir: String = mPackagePath + "/tmp"
      val dir: File = new File(workingDir)
    
      if (!dir.exists) {
        if (!dir.mkdir) {
          Console.err.println(s"Failed to make working directory $workingDir")
          return 1
        }
      }
      
      // Set directory to be writable by anyone (req'd for vssrt)
      dir.setWritable(true, false)

      // Copy the pkg_common and template scripts to the Packaging directory
      copyFile("pkg_sysmon", mTOP + "/Inst/src", mPackagePath)
      copyFile("pkg_common", mTOP + "/Inst/src", mPackagePath)
      
      val pkgSysMon: java.util.ArrayList[String] = new java.util.ArrayList[String]
      
      pkgSysMon.add("ssh")
      pkgSysMon.add("-t")
      pkgSysMon.add("-t")
      pkgSysMon.add("vssrt@" + mPackageServer)
      pkgSysMon.add("export")
      pkgSysMon.add("TOP=" + mTOP + ";")
      pkgSysMon.add(mPackagePath + "/pkg_sysmon")
      pkgSysMon.add("-p")
      pkgSysMon.add(mPackagePath + "/DVD_Images")
      pkgSysMon.add("-w")
      pkgSysMon.add(workingDir)
      pkgSysMon.add("-o")
      pkgSysMon.add(mPackagePath + "/vm_sysmon.ova")
      pkgSysMon.add("-version")
      pkgSysMon.add(mBuildTag.get)
      pkgSysMon.add("-u")
      pkgSysMon.add(mUserName)
      
      var i: Int = 1
      
      println("Packaging command:")
      
      import scala.collection.JavaConversions._
      
      for (e <- pkgSysMon) {
        println(s"Arg $i : $e")
        i += 1
      }
      
      mPB = new ProcessBuilder(pkgSysMon)
      mPB.directory(new File(mPackagePath))
      
      println("Starting pkg_sysmon")
      
      mProc = mPB.start
      
      val name: Option[String] = mPackageLogs.get(mPackageStep.toString)
      
      name match {
        case Some(name) =>
          val logname: String = mLogPath + "/" + name
          val log: File = new File(logname)
          
          if (log.exists) {
            println("Deleting old log")
            log.delete
          }
          
          val instr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mInputReader = new StreamReader(mProc.getInputStream, "INPUT: ", instr)
          
          val errstr: java.io.OutputStream = new java.io.FileOutputStream(logname, true)
          
          mErrorReader = new StreamReader(mProc.getErrorStream, "ERROR: ", errstr)
          mInputReader.start
          mErrorReader.start
          
          println("Waiting for pkg_sysmon")
          
          mProc.waitFor
          rc = mProc.exitValue
          
          println(s"Exit value $rc")
          rc

        case None =>
          Console.err.println(s"Missing log file name for step ${mPackageStep}")
          1
      }
    }
    catch {
      case ex: java.io.IOException => {
        Console.err.println("Caught IO Exception: " + ex.getMessage)
        rc = -116
        rc
      }
      case ex: InterruptedException => {
        Console.err.println("Caught Interrupted Exception")
        rc = 1
        mProc.destroy
        rc
      }
    } finally {
      rc
    }
  }

  private def doBuild: Int = {
    var rc: Int = 0
    
    try {
      mBuildStep = BuildStep.NONE
      mFailedBuildStep = BuildStep.NONE
      mID = getId
    
      if (mID == None) {
        return 1
      }

      if (mCreateODBUser) {
        rc = createODBUser
      
        if (rc != 0) {
          Console.err.println("Create new ODB user failed.")
          return rc
        }
      }
      else {
        if (mODBUser != None) {
          println(s"Reusing existing ODB user ${mODBUser.get}")
          rc = checkODB
      
          if (rc != 0) {
            return rc
          }
        }
      }
      
      if (mRunBuild) {
        // Check version of SS we are building. vss_build only came in 9.2 and 
        // later. Earlier versions will use jam and BldDatabase.
        if (mIsLegacyServer) {
          rc = jamExports
      
          if (rc != 0) {
            Console.err.println("jam exports failed.")
            return rc
          }
      
          if (mODBUser != None && mODBPassword != None && mODBInstance != None && mRunBuildODB) {
            rc = bldDatabase
            if (rc != 0) {
              Console.err.println("BldDatabase failed.")
              return rc
            }
          }
        }
        else {
          // For 9.2 and later, we'll use vss_build
          if (mRunBuildClean) {
            rc = vssbuildclean
      
            if (rc != 0) {
              Console.err.println("vss_build clean failed.")
              return rc
            }
      
            println("Removing queues directory.")
            deleteDirectory(s"{mTOP.get}/queues")
          }
          rc = vssbuild
          if (rc != 0) {
            Console.err.println("vss_build failed.")
            return rc
          }
        }
        
        if (mRunBuildODB) {
          rc = loadJavaPrivileges
        
          if (rc != 0) {
            Console.err.println("Grant Java privileges failed.")
            return rc
          }
        }
      }
      
      if (mRunSetup) {
        if (mRunBldRuntime) {
          // Remove the old CDS_TOP if BldRuntime and Rebuilding the ODB were
          // both specified. This is to prevent the LDAP directory from being
          // out of sync
          if (mRunBuildODB) {
            println("Removing CDS_TOP directory.")
            deleteDirectory(mCDSTOP.get)
          }
          
          rc = bldRuntime(mODBUser.get, mODBPassword.get, mODBInstance.get)
          
          if (rc != 0) {
            Console.err.println("BldRuntime failed.")
            return rc
          }
          
          // If we are running in Clearcase, then move the binaries outside
          // of the VOB and create symlinks
          if (mIsClearCase) {
            rc = moveDirectories
            
            if (rc != 0) {
              Console.err.println("Moving directories failed.")
              return rc
            }
          }
        }
        
        if (mRunDataLoader) {
          rc = loadCompose
        
          if (rc != 0) {
            Console.err.println("Compose deployment failed.")
            return rc
          }
        
          rc = startLdap
          if (rc != 0) {
            Console.err.println("StartLdap failed.")
            return rc
          }
        
          rc = dataLoader
        
          if (rc != 0) {
            Console.err.println("DataLoader failed.")
            return rc
          }
        
          rc = stopLdap
        
          if (rc != 0) {
            Console.err.println("StopLdap failed.")
            return rc
          }
        
          rc = postDataLoader
        
          if (rc != 0) {
            Console.err.println("Post DataLoader failed.")
            return rc
          }
        }
        
        rc = systemID
        
        if (rc != 0) {
          Console.err.println("SystemID failed.")
          return rc
        }
      }
      
      if (mRunJUnit) {
        rc = jUnit
      
        if (rc != 0) {
          Console.err.println("JUnit run failed.")
          return rc
        }
      }
      
      if (mRunFindBugs) {
        rc = findBugs
      
        if (rc != 0) {
          Console.err.println("FindBugs failed.")
          return rc
        }
      }
    }
    catch {
      case ex: Exception => {
        Console.err.println("doBuild: " + ex.getMessage)
        ex.printStackTrace
      }
    } finally {  // Drop the ODB schema if it was created for Jenkins and copy the logs
      
      // If the build step failed , save the failed step
      if (rc != 0) {
        mFailedBuildStep = mBuildStep
      }
      
      // Log final step if everything was successful
      if (rc == 0) {
        setBuildStep(BuildStep.DONE, "Build Done")
      }
    }
    
    rc
  }

  private def doPackaging: Int = {
    var rc: Int = 0
    
    try {
      System.out.println("Running Packaging...")
      mPackageStep = PackageStep.NONE
      mFailedPackageStep = PackageStep.NONE
    
      // Service Suite 9.1 and earlier use jam release
      if (mIsLegacyServer) {
        rc = jamRelease
    
        if (rc != 0) {
          Console.err.println("jam release failed.")
          return rc
        }
      }
      else {
        // Service Suite 9.2 and later use vss_build release
        rc = vss_build_release
        if (rc != 0) {
          Console.err.println("vss_build release failed.")
          return rc
        }
      }
    
      // If no ODB credentials passed, attempt to obtain it from StartEnv
      if (mODBUser == None || mODBPassword == None) {
        rc = getStartEnv
    
        // If StartEnv failed to get the ODB info, try and create the ODB
        // user automatically (this is the case for AutoPackage).
        if (rc != 0) {
          rc = createODBUser
    
          if (rc != 0) {
            Console.err.println("Create new ODB user failed.")
            return rc
          }
        }
      }
    
      rc = pkgAll
    
      if (rc != 0) {
        Console.err.println("pkg_all failed.")
        return rc
      }
    
      if (mRunPackageVM) {
        rc = pkgAppliance
        if (rc != 0) {
          Console.err.println("pkg_appliance failed.")
          return rc
        }
    
        rc = pkgSOI
    
        if (rc != 0) {
          Console.err.println("pkg_soi failed.")
          return rc
        }
    
        rc = pkgSysMon
    
        if (rc != 0) {
          Console.err.println("pkg_sysmon failed.")
          return rc
        }
      }
    }
    catch {
      case ex: Exception => {
        Console.err.println("doPackaging: " + ex.getMessage)
        ex.printStackTrace
      }
    } finally {
      // If the build step failed, save the failed step
      if (rc != 0) mFailedPackageStep = mPackageStep

      // Insert code here to copy logs, if any
      // For now, there are no additional logs/artifacts to copy    
      setPackageStep(PackageStep.COPY_LOGS, "Copying Logs")
    
      // Log final step only if everything was successful
      if (rc == 0) setPackageStep(PackageStep.DONE, "Packaging Done")
    }
    
    rc
  }

  private def writeResult(success: Boolean, stepname: String, msg: String) {
    
    val prop: Properties = new Properties
    
    try {
      if (success) {
        prop.setProperty("RESULT", "PASS")
      }
      else {
        prop.setProperty("RESULT", "FAIL")
        if (stepname != null) {
          prop.setProperty("FAILED_STEP", stepname)
        }
        if (msg != null) {
          prop.setProperty("FAILED_MSG", msg)
        }
      }
    
      // First, try and use WORKSPACE. If not set (because the user is running
      // this from command line and not from Jenkins, then default to the log
      // path to store the build result.
      var path: String = System.getenv("WORKSPACE")
    
      if (path == null) path = mLogPath

      prop.store(new java.io.FileOutputStream(path + "/BldServerResult.properties"), "BldServer report")
    }
    catch {
      case ex: java.io.IOException => {
        ex.printStackTrace
      }
    }
  }

  def start: Int = {
    var rc: Int = 0

    try {
      val dir: java.io.File = new java.io.File(mLogPath)

      if (! dir.exists) {
        if (! dir.mkdir) {
          Console.err.println("Failed to make log directory " + mLogPath)
          rc = 1
          return rc
        }
      }

      if (mRunBuild || mRunSetup || mRunJUnit || mRunFindBugs) {
        rc = doBuild

        if (rc != 0) {
          println(s"\n*** Failed Step ${mFailedBuildStep.id} - ${mFailedBuildStep} ***")
          
          val logname: Option[String] = mBuildLogs.get(mFailedBuildStep.toString)
          
          if (logname != None)  println(s"Failed Logs: ${mLogPath}/${logname.get}")

          writeResult(false, mBuildStep.toString, "Some build message")

          return rc
        }
      }

      if (mRunPackage) {
        rc = doPackaging

        if (rc != 0) {
          println(s"\n*** Failed Step ${mFailedPackageStep.id} - ${mFailedPackageStep} ***")
          
          val logname: Option[String] = mPackageLogs.get(mFailedPackageStep.toString)
          
          if (logname != None) println(s"Failed Logs: ${mLogPath}/${logname}")
          
          writeResult(false, mPackageStep.toString, "Some packaging message")
          
          return rc
        }
      }
    } finally {
      if (mDropODBUser && mODBUser != None) {
        println("\n*** Dropping ODB user ***")
        
        rc = dropDatabase
        
        if (rc != 0) {
          Console.err.println("Drop ODB user failed.")
          return rc
        }
      }
    }
    
    writeResult(true, null, null)
    
    println("\n*** Finish ***")
    
    return rc
  }
}
