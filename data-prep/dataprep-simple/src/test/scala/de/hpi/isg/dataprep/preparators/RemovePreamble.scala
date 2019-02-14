package de.hpi.isg.dataprep.preparators

import de.hpi.isg.dataprep.components.Preparation
import de.hpi.isg.dataprep.exceptions.ParameterNotSpecifiedException
import de.hpi.isg.dataprep.preparators.define.ChangeDateFormat
import de.hpi.isg.dataprep.preparators.implementation.{DateRegex, DefaultChangeDateFormatImpl}
import de.hpi.isg.dataprep.util.DatePattern.DatePatternEnum
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import de.hpi.isg.dataprep.preparators.implementation.DefaultRemovePreambleImpl
import org.apache.spark.ml.clustering.BisectingKMeans
import org.apache.spark.ml.feature.Word2Vec
import org.apache.spark.ml.linalg.{DenseVector, Vector, Vectors}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{LongType, StringType, StructField, StructType}

import scala.collection.JavaConverters._
import scala.collection.mutable

class RemovePreamble extends PreparatorScalaTest {

  var sparkContext:SparkSession = _

  override def beforeAll: Unit = {
    val sparkBuilder = SparkSession
      .builder()
      .appName("SparkTutorial")
      .master("local[4]")
    sparkContext = sparkBuilder.getOrCreate()
    super.beforeAll()
  }

  "Initial char" should "find right counts for each char" in {
    val localContext = sparkContext
    import localContext.implicits._
    val customDataset = Seq(("1","2"), ("3","4"), ("#postamble",""),("#postamble",""),("#postamble",""), ("","fake")).toDF
    customDataset.columns.length shouldEqual(2)
    val test = customDataset
        .map {r => r.mkString.charAt(0).toString}
      .filter(c => !"[A-Za-z0-9]".r.pattern.matcher(c).find())
        .groupBy("value")
        .count()
        .toDF("char", "count")
        .reduce((a,b) => if (a.getLong(1) > b.getLong(1)) a else b )

    test shouldEqual Row("#",3)
  }

  "Initial char" should "remove all preable lines starting with #" in {
    val localContext = sparkContext
    import localContext.implicits._
    val customDataset = Seq(("1","2"), ("3","4"), ("#postamble",""),("#postamble",""),("#postamble",""), ("","fake")).toDF
    val expectedDataset = Seq(("1","2"), ("3","4"), ("","fake")).toDF

    val prep = new DefaultRemovePreambleImpl

    val result = prep.analyseLeadingCharacter(customDataset)

    result.collect shouldEqual expectedDataset.collect
  }

  "Remove Preamble" should "calculate calApplicability for multiple collumns correctly" in {
    val localContext = sparkContext
    import localContext.implicits._
    val customDataset = Seq(("1","2"), ("3","4"), ("postamble",""),("postamble",""),("postamble",""), ("","fake")).toDF
    customDataset.columns.length shouldEqual(2)
    val result = customDataset
        .map(row => row.mkString)
    .map(_.toString)
    .collect
    .reduce( _+ "|" + _)
    result shouldEqual("12|34|postamble|postamble|postamble|fake")
  }

  "Remove Preamble" should "find the correct first char in each line" in {
    val localContext = sparkContext
    import localContext.implicits._
    val customDataset = Seq(("1","2"), ("3","4"), ("postamble",""),("postamble",""),("postamble",""),("1", "3"), ("","fake")).toDF
    customDataset.columns.length shouldEqual(2)

    val test = customDataset
      .rdd
      .zipWithIndex()
      .map(e => (e._1.toString().charAt(1),List(e._2)))
      .reduceByKey(_.union(_))
      .flatMap(row => row._2.groupBy(k => k - row._2.indexOf(k)).toList.map(group => (row._1, group._2.size)))
      .filter(row => row._1.toString.matches("[^0-9]"))
    .map(_.toString)
    .collect
    .reduce( _+ "|" + _)
    test shouldEqual("(p,3)|(,,1)")
  }

  "Clustering" should "find correct clusters in dataset with separators" in {
    val localContext = sparkContext
    import localContext.implicits._
    val customDataset = Seq("1,2", "3,4", "postamble ", "postamble ","postamble ","1,3", "7,8,").toDF
    customDataset.columns.length shouldEqual(1)

    val testPreparator = new DefaultRemovePreambleImpl
    val prepResult = testPreparator.findPreambleByClustering(customDataset, ",")
    prepResult.collect() shouldEqual Array(Row("1,2"), Row("3,4"), Row("1,3"), Row("7,8,"))
  }

  /*"Median" should "calculate median" in {
    val rdd: RDD[Int] = sc.parallelize(Seq((1), (2), (5), (2)))

    val testPreparator = new DefaultRemovePreambleImpl
    val median = testPreparator.calculateMedian(rdd)

    median shouldEqual 2
  }*/


  "ClusteringMedian" should "find clusters in dataset with separators by calculate the median" in {
    val localContext = sparkContext
    import localContext.implicits._
    val dataset = Seq("3,4", "2,4", "postamble ", "postamble ","postamble ","8,7", "9,1").toDF
    dataset.columns.length shouldEqual(2)

    val testPreparator = new DefaultRemovePreambleImpl
    val prepResult = testPreparator.findPreambleByMedian(dataset, ",")
    prepResult.collect() shouldEqual Array(Row("1,2"), Row("3,4"), Row("1,3"), Row("7,8,"))
  }

  "Word2Vec" should "identify special values in each colum" in {
    val localContext = sparkContext
    import localContext.implicits._
    val customDataset = Seq(("0.01","2"), ("0.03","4"), ("postamble",""),("0.1", "3"), ("","fake")).toDF("f","s")
    customDataset.columns.length shouldEqual(2)

    val testPreparator = new DefaultRemovePreambleImpl
    //val prepResult = testPreparator.outlierWordToVec(customDataset)

    val word2Vec = new Word2Vec()
      .setInputCol("value")
      .setOutputCol("features")
      .setVectorSize(100)
      .setMinCount(0)

    val zippedDataset = customDataset
      .rdd
      .zipWithIndex()
      .flatMap(row => row._1.toSeq.map( v => (v.toString.split(""), row._2, row._1.toSeq.indexOf(v))))
      .toDF("value", "line", "column")

    val oneColumnDataset = zippedDataset.filter(r => r.getInt(2) == 0)

    val model = word2Vec.fit(oneColumnDataset)
    val result = model.transform(oneColumnDataset)
    val bkm = new BisectingKMeans().setK(2).setSeed(1)
    val modelBM = bkm.fit(result)

    val clusteredVecs = modelBM
      .transform(result)
        .toDF
        .rdd
        .map { r =>
          (r.getAs[mutable.WrappedArray[String]](0), r.getLong(1), r.getAs[Int](2), r.getInt(4))
        }
      .persist
      .toDF("value","line", "column", "cluster")

    val largestCluster = clusteredVecs.stat.approxQuantile("cluster",Array(0.5),0.1).head
    val filteredClusters = clusteredVecs.filter(row => row.getInt(1) == largestCluster).map(row => row.getString(0))

    clusteredVecs.collect() shouldEqual "DataFrame"
  }
  "Word2Vec" should "identify special values in each column in preparator" in {
    val localContext = sparkContext
    import localContext.implicits._
    val customDataset = Seq(("0.01","2"), ("0.03","4"),("0.1", "3"), ("something","fake"),("postamble","wsdf")).toDF("f","s")
    customDataset.columns.length shouldEqual(2)

    val testPreparator = new DefaultRemovePreambleImpl

    val zippedDataset = customDataset
      .rdd
      .zipWithIndex()
      .flatMap(row => row._1.toSeq.map( v => (v.toString.split(""), row._2, row._1.toSeq.indexOf(v))))
      .toDF("value", "line", "column")

    val oneColumnDataset = zippedDataset.filter(r => r.getInt(2) == 0)

    val clusteredVecs = testPreparator.findPreableForColumn(oneColumnDataset, localContext)

    clusteredVecs.collect().length shouldEqual 3
  }

  "Word2Vec" should "combine columns correctly" in {
    val localContext = sparkContext
    import localContext.implicits._
    val fileData = localContext.read
      .option("sep", "\t")
      .csv("../dataprep-simple/src/test/resources/test_space_preamble.csv")
    val customDataset = fileData
    val customDataset1 = Seq(("0.01","2", "wow"),
                            ("0.03","4", "well"),
                            ("0.1", "3", "watson"),
                            ("something","fake", "shit"),
                            ("postamble","wsdf", "lsjkd")).toDF("f","s", "d")
    customDataset.columns.length shouldEqual(3)

    val testPreparator = new DefaultRemovePreambleImpl

    val zippedDataset = customDataset
      .rdd
      .zipWithIndex()
      .flatMap(row => row._1.toSeq.map( v => (v.toString.split(""), row._2, row._1.toSeq.indexOf(v))))
      .toDF("value", "line", "column")

    var emptyDataFrame:DataFrame = Seq(Tuple3("a","b", Seq("c"))).toDF("a", "b", "c").filter(r => r.getString(0) != "a")

    for(columnIndex <- customDataset.columns.indices){
      val colResult = testPreparator
        .findPreableForColumn(zippedDataset.filter(r => r.getInt(2) == columnIndex), sparkContext)
        .select("line", "column", "value")
        emptyDataFrame = emptyDataFrame.union(colResult)

    }

    emptyDataFrame.collect() shouldEqual(2)
  }

  "CharTypeClusterer" should "generate Vectors correctly" in {
    val localContext = sparkContext
    import localContext.implicits._
    val fileData = localContext.read
      .option("sep", "\t")
      .csv("../dataprep-simple/src/test/resources/test_space_preamble.csv")
    val customDataset = fileData
    customDataset.columns.length shouldEqual(3)

    val testPreparator = new DefaultRemovePreambleImpl

    val zippedDataset = testPreparator.findPreableByTypesOfChars(customDataset)

    zippedDataset.filter(row => row.get(2) == 1).collect.length shouldEqual 15
  }
}