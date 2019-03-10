package com.shujia.util

import org.apache.commons.exec.{CommandLine, DefaultExecutor, PumpStreamHandler}

object CommandUtil {

  /**
    * 执行命令
    * java调用linux shell
    */
  def execute(cmd: String): Unit = {
    try {
      val commandline = CommandLine.parse(cmd)
      val exec = new DefaultExecutor
      exec.setExitValue(0)
      val streamHandler = new PumpStreamHandler(System.out, System.err)
      exec.setStreamHandler(streamHandler)
      exec.execute(commandline)
    } catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
  }
}
