package com.shujia.rtmroad

import java.util
import com.shujia.common.SparkTool
import com.shujia.constant.Constants
import com.shujia.util.JDBCHelper
import kafka.serializer.StringDecoder
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Durations, StreamingContext}

/**
  * 缉查布控
  *
  * 1、通过Spark Streaming 连接Kafka读取数据车流量数据
  * 2、从数据库查询缉查布控的车牌号
  * 3、过滤出现的车辆
  * 4、将结果存到数据库
  *
  */
object ControlCars extends SparkTool {
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
    //构建spark streaming 上下文对象
    val ssc = new StreamingContext(sc, Durations.seconds(5))

    /**
      * 连接kafka
      * 使用direct模式
      */
    val flowDS = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](
      ssc,
      Map("metadata.broker.list" -> Constants.KAFKA_BROKER_LIST),
      Constants.TOPICS.split(",").toSet
    )

    /**
      * 缉查布控
      */

    /**
      * 动态修改广播变量
      *
      * flowDS.map(_._2).foreachRDD ：每个batch都会执行一次
      *
      */
    flowDS.map(_._2).foreachRDD(rdd => {
      val context = rdd.context

      /**
        * 读取mysql,查询需要缉查布控的车辆
        */
      val sqlContext = new SQLContext(context)
      val arsDF = sqlContext.read.format("jdbc").options(
        Map(
          "url" -> Constants.JDBC_URL,
          "driver" -> Constants.JDBC_DRIVER,
          "dbtable" -> "control_cars",
          "user" -> Constants.JDBC_USER,
          "password" -> Constants.JDBC_PASSWORD
        )).load()

      val carList = arsDF
        .select("car")
        .rdd
        .collect()
        .toList
        .map(_.getAs[String]("car"))

      //每一次batch都会广播一次
      val carListBroadcast = sc.broadcast(carList)

      LOGGER.info("布控的车辆：" + carList.mkString(","))

      val controlCarRDD = rdd.filter(l => {
        //LOGGER.info(l)
        val cars = carListBroadcast.value
        val car = l.split("\t")(3)
        cars.contains(car)
      })


      /**
        * 将结果存到数据库
        */
      controlCarRDD.foreachPartition(i => {

        val sql = "INSERT INTO control_flow VALUES(?,?,?,?,?,?,?,?)"
        val helper = JDBCHelper.getInstance()
        i.foreach(line => {
          val list = new util.ArrayList[Array[String]]()
          list.add(line.split("\t"))
          helper.executeBatch(sql, list)
        })
      })

    })

    ssc.start()
    ssc.awaitTermination()
    ssc.stop()

  }
}

/**
  * 操作流程：
  * 打jar包，上传至集群中，解压jar包
  * 打开zookeeper和kafka
  * 在解压后的bin目录里执行脚本：
  * bash mock_real_time_data.sh 生产数据
  * bash flume_to_kafka.sh 将数据放到kafka中
  * 在本地数据库中创建traffic库control_cars和control_flow表
  *
  * 测试：
  * 在集群/tmp/flume/data.log目录里查看生产的车辆数据
  * kafka生产需要布控的车辆数据
  * 将需要布控的车牌号添加到control_cars表里
  * 运行ControlCars.scala代码
  * 在control_flow表里显示出布控到的数据
  */
//Exception in thread "main" org.apache.spark.SparkException: java.nio.channels.ClosedChannelException
//hadoop.util.Shell - Failed to locate the winutils binary in the hadoop binary path
//java.io.IOException: Could not locate executable null\bin\winutils.exe in the Hadoop binaries.


/**
  * 在每台节点启动broker
  * nohup kafka-server-start.sh /usr/local/share/kafka_2.10-0.8.2.2/config/server.properties >> /usr/local/share/kafka_2.10-0.8.2.2/logs/start.log 2>&1 &
  *
  * 创建控制台生产者
  * kafka-console-producer.sh --broker-list master:9092,node1:9092,node2:9092 --topic test_topic
  *
  * 创建控制台消费者
  * kafka-console-consumer.sh --zookeeper  master:2181,node1:2181,node2:2181  --from-beginning --topic test_topic
  */