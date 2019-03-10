package com.shujia.rtmroad

import java.util.Date

import com.shujia.common.SparkTool
import com.shujia.common.boot.HadoopUtil
import com.shujia.constant.Constants
import com.shujia.util.DateUtils
import org.apache.hadoop.fs.{FileSystem, Path}

object MergeCarFlowTmpData extends SparkTool {
  /**
    * spark配置初始化方法，初始化conf对象
    */
  override def init(args: Array[String]): Unit = {
    conf.setMaster("local")
  }

  /**
    * spark主逻辑方法
    * 该方法内不能配置conf
    */
  override def run(args: Array[String]): Unit = {

    /**
      * 获取前一分钟目录列表
      */
    val time = DateUtils.formatTimeMinute(new Date())
    val lastTIme = DateUtils.getLastMinute(time)

    //输入数据路径
    val inputPath = Constants.CAR_FLOW_OUT_PUT_PATH_TMP + "/time=" + lastTIme + "*"

    val RDD1 = sc.textFile(inputPath)

    val outPutPath = Constants.CAR_FLOW_OUT_PUT_PATH+"/time="+lastTIme

    //删除输出目录
    val fileSystem = FileSystem.get(HadoopUtil.getHadoopConfig)
    if (fileSystem.exists(new Path(outPutPath))) {
      //删除输出目录
      fileSystem.delete(new Path(outPutPath), true)
    }

    /**
      * 数据存到hdfs
      */
    RDD1
      .coalesce(6, false)
      .saveAsTextFile(outPutPath)



  }
}
