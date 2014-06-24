package io.prediction.tools

import io.prediction.storage.Config

import grizzled.slf4j.Logging

import scala.sys.process._

import java.io.File

object RunEvaluationWorkflow extends Logging {
  def main(args: Array[String]): Unit = {
    case class Args(
      sparkHome: String = "",
      id: String = "",
      version: String = "",
      batch: String = "",
      dataPrepJsonPath: String = "dataPrepParams.json",
      validatorJsonPath: String = "validatorParams.json",
      cleanserJsonPath: String = "cleanserParams.json",
      algoJsonPath: String = "algoParams.json",
      serverJsonPath: String = "serverParams.json",
      jsonDir: String = ".")

    val parser = new scopt.OptionParser[Args]("RunEvaluationWorkflow") {
      arg[String]("<engine id>") action { (x, c) =>
        c.copy(id = x)
      } text("Engine ID.")
      arg[String]("<engine version>") action { (x, c) =>
        c.copy(version = x)
      } text("Engine version.")
      opt[String]("spark") action { (x, c) =>
        c.copy(sparkHome = x)
      } text("Path to a Apache Spark installation. If not specified, the SPARK_HOME environmental variable will be used.")
      opt[String]("batch") action { (x, c) =>
        c.copy(batch = x)
      } text("Batch label of the run.")
      opt[String]("jsonDir") action { (x, c) =>
        c.copy(jsonDir = x)
      } text("Base directory of JSON files. Default: .")
      opt[String]("dp") action { (x, c) =>
        c.copy(dataPrepJsonPath = x)
      } text("Data preparator parameters file. Default: dataPrepParams.json")
      opt[String]("vp") action { (x, c) =>
        c.copy(validatorJsonPath = x)
      } text("Validator parameters file. Default: validatorParams.json")
      opt[String]("cp") action { (x, c) =>
        c.copy(cleanserJsonPath = x)
      } text("Cleanser parameters file. Default: cleanserParams.json")
      opt[String]("ap") action { (x, c) =>
        c.copy(algoJsonPath = x)
      } text("Algorithm parameters file. Default: algoParams.json")
      opt[String]("sp") action { (x, c) =>
        c.copy(serverJsonPath = x)
      } text("Server parameters file. Default: serverParams.json")
    }

    parser.parse(args, Args()) map { parsedArgs =>
      val config = new Config
      val engineManifests = config.getSettingsEngineManifests
      val defaults = Args()
      engineManifests.get(parsedArgs.id, parsedArgs.version) map { engineManifest =>
        val sparkHome = if (parsedArgs.sparkHome != "") parsedArgs.sparkHome else sys.env.get("SPARK_HOME").getOrElse(".")
        val params = Map(
          "dp" -> parsedArgs.dataPrepJsonPath,
          "vp" -> parsedArgs.validatorJsonPath,
          "cp" -> parsedArgs.cleanserJsonPath,
          "ap" -> parsedArgs.algoJsonPath,
          "sp" -> parsedArgs.serverJsonPath)
        Seq(
          s"${sparkHome}/bin/spark-submit",
          "--verbose",
          "--master spark://duckpond:7077",
          "--class io.prediction.tools.CreateEvaluationWorkflow",
          engineManifest.jar,
          "--engineManifestId",
          engineManifest.id,
          "--engineManifestVersion",
          engineManifest.version,
          "--evaluatorFactory",
          engineManifest.evaluatorFactory,
          "--engineFactory",
          engineManifest.engineFactory,
          if (parsedArgs.batch != "") "--batch " + parsedArgs.batch else "",
          if (params("dp") == defaults.dataPrepJsonPath && !(new File(withPath(params("dp"), parsedArgs.jsonDir))).exists) "" else "--dp " + withPath(params("dp"), parsedArgs.jsonDir),
          if (params("vp") == defaults.validatorJsonPath && !(new File(withPath(params("vp"), parsedArgs.jsonDir))).exists) "" else "--vp " + withPath(params("vp"), parsedArgs.jsonDir),
          if (params("cp") == defaults.cleanserJsonPath && !(new File(withPath(params("cp"), parsedArgs.jsonDir))).exists) "" else "--cp " + withPath(params("cp"), parsedArgs.jsonDir),
          if (params("ap") == defaults.algoJsonPath && !(new File(withPath(params("ap"), parsedArgs.jsonDir))).exists) "" else "--ap " + withPath(params("ap"), parsedArgs.jsonDir),
          if (params("sp") == defaults.serverJsonPath && !(new File(withPath(params("sp"), parsedArgs.jsonDir))).exists) "" else "--sp " + withPath(params("sp"), parsedArgs.jsonDir)
        ).mkString(" ").!
      } getOrElse {
        error(s"Engine ${parsedArgs.id} ${parsedArgs.version} is not registered.")
      }
    }
  }

  private def withPath(file: String, path: String) = s"$path/$file"
}
