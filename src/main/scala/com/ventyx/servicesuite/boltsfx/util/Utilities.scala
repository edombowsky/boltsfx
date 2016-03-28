package com.abb.unitconverter

import java.io.{IOException, FileNotFoundException}
import java.io.{FileOutputStream, OutputStream, FileInputStream}
import java.util.Properties

object Utilities {

  private val profileName = "Bolt.ini"

  def readProfile(props: java.util.Properties, path: String): Unit = {
 
    try {
      props.load( new FileInputStream(path + "/" + profileName) );
      println(s"Using properties ${path}/${profileName}")
    } catch {
      case ex: FileNotFoundException => 
        println("No properties file found. New one will be created.");
      case ex: IOException => Console.err.println(ex.getMessage)
    }
  }

  def writeProfile(props: java.util.Properties, path: String): Unit = {
 
    var output: OutputStream = null;
    try {
      output = new FileOutputStream(s"${path}/${profileName}")
      props.store(output, null)
    }
    catch {
      case ex: IOException => Console.err.println(ex.getMessage)
    }
    finally {
      output.close
    }
  }

  def getSystemEnv(envName: String): String = {

    val systemEnv: Option[String] = sys.env.get(envName)

    if (systemEnv.isEmpty) {

      println(s"Please set ${envName} environment variable and restart Bolt.")
        
      sys.exit(1)
    }

    systemEnv.get
  }
}
