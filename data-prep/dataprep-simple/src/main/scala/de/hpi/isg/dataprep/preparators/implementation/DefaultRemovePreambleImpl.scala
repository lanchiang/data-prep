package de.hpi.isg.dataprep.preparators.implementation

import com.sun.rowset.internal.Row
import de.hpi.isg.dataprep.components.AbstractPreparatorImpl
import de.hpi.isg.dataprep.model.error.PreparationError
import de.hpi.isg.dataprep.model.target.system.AbstractPreparator
import de.hpi.isg.dataprep.preparators.define.RemovePreamble
import de.hpi.isg.dataprep.ExecutionContext
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkContext, sql}
import org.apache.spark.sql._
import org.apache.spark.util.CollectionAccumulator
import org.apache.spark.ml.clustering.{BisectingKMeans, KMeans}
import org.apache.spark.ml.feature.{RFormula, Tokenizer, Word2Vec}
import org.apache.spark.ml.linalg.{Vector, VectorUDT}
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.ml.linalg.SQLDataTypes.VectorType

import scala.collection.mutable
/**
  *
  * @author Lasse Kohlmeyer
  * @since 2018/11/29
  */
class DefaultRemovePreambleImpl extends AbstractPreparatorImpl {
  private var preambleCharacters = "[!#$%&*+-./:<=>?@^|~].*$"

  //ideas: average distance of characters in line

  override protected def executeLogic(abstractPreparator: AbstractPreparator,
                                      dataFrame_orig: Dataset[sql.Row],
                                      errorAccumulator: CollectionAccumulator[PreparationError]): ExecutionContext = {

    val dataFrame = dataFrame_orig.toDF
    val preparator = abstractPreparator.asInstanceOf[RemovePreamble]
    val delimiter = preparator.delimiter
    val hasHeader = preparator.hasHeader.toBoolean
    val hasPreamble = preparator.hasPreamble
    var endPreambleIndex = preparator.rowsToRemove
    val commentCharacter = preparator.commentCharacter

    if (hasPreamble == false) return new ExecutionContext(dataFrame, errorAccumulator)


    //identifying last preamble row
    if (endPreambleIndex == 0) {
      endPreambleIndex = findRow(dataFrame.toDF)
    }
    if (endPreambleIndex == 0 && (hasPreambleInColumn(dataFrame) == false)) {
      return new ExecutionContext(dataFrame, errorAccumulator)
    }

    //taking char character as preamble character
    if (commentCharacter.equals("") == false) {
      preambleCharacters = commentCharacter + ".*$"
    }
    //drop all rows before last preamble row and update column names

    //creating new dataframe to frame of original dataframe
    //val filteredDatasetReloaded = newDataFrameToPath(dataFrame.inputFiles(0), endPreambleIndex, dataFrame.toDF, delimiter, hasHeader)
    var filteredDataframe = dataFrame

    try {
      filteredDataframe = newHeadForDataFrame(removePreambleRecursive(dataFrame.toDF, endPreambleIndex), dataFrame)
    } catch {
      case e: Exception =>

        filteredDataframe
    }

    //if column size is identical use modified dataframe, else use new parsed dataframe
    if (filteredDataframe.columns.length != dataFrame.columns.length) {
      filteredDataframe = dataFrame
    }

    val ergDataset = filteredDataframe
    new ExecutionContext(ergDataset, errorAccumulator)
  }

  def outlierWordToVec(dataframe:DataFrame): DataFrame = {
    val sparkBuilder = SparkSession
      .builder()
      .appName("SparkTutorial")
      .master("local[4]")
    val sparkContext = sparkBuilder.getOrCreate()
    import sparkContext.implicits._

    val zippedDataset = dataframe
      .rdd
      .zipWithIndex()
      .flatMap(row => row._1.toSeq.map( v => (v.toString.split(""), row._2, row._1.toSeq.indexOf(v))))
      .toDF("value", "line", "column")

    for(columnIndex <- dataframe.columns.indices){
      findPreableForColumn(zippedDataset.filter(r => r.getInt(2) == columnIndex), sparkContext)
    }
    dataframe
  }

  def findPreableForColumn(dataframe:DataFrame, sparkContext: SparkSession): DataFrame  = {

    import sparkContext.implicits._
    val word2Vec = new Word2Vec()
      .setInputCol("value")
      .setOutputCol("features")
      .setVectorSize(100)
      .setMinCount(0)
    val model = word2Vec.fit(dataframe)
    val result = model.transform(dataframe)
    val bkm = new BisectingKMeans().setK(2).setSeed(1)
    val modelBM = bkm.fit(result)

    val clusteredVecs = modelBM
      .transform(result)
      .rdd
      .map { r =>
        (r.getAs[mutable.WrappedArray[String]](0), r.getAs[Int](2))
      }
      .persist
      .toDF("value", "cluster")

    val largestCluster = clusteredVecs.stat.approxQuantile("cluster",Array(0.5),0.1).head

    clusteredVecs
  }

  def findPreambleByClustering(dataframe: DataFrame,  separator: String): DataFrame ={

    val (sparkContext: SparkSession, separatorOcc: DataFrame) = createBuilderAndFindSeparator(dataframe, separator)
    import sparkContext.implicits._
    val kmeans = new KMeans().setK(2).setSeed(1L)
    val model = kmeans.fit(separatorOcc)

    val predictions = model.transform(separatorOcc)

    val maxCluster = predictions
      .groupBy("prediction")
      .count()
      .collect
      .maxBy(row => row.getLong(1))
      .getInt(0)

    val filteredLines = predictions
      .filter(row => row.getAs("prediction") == maxCluster)
      .select("rownumber")
      .map(row => row.getLong(0))
      .collect

    val resultRDD = dataframe
      .rdd.zipWithIndex()
      .filter(row => filteredLines contains row._2)
      .map(row => row._1)
      .persist

    sparkContext.createDataFrame(resultRDD, dataframe.schema)
  }

  private def createBuilderAndFindSeparator(dataframe: DataFrame, separator: String) = {
    val sparkBuilder = SparkSession
      .builder()
      .appName("SparkTutorial")
      .master("local[4]")
    val sparkContext = sparkBuilder.getOrCreate()
    import sparkContext.implicits._

    val separatorOcc = dataframe
      .rdd
      .zipWithIndex()
      .map(row => (Vectors.dense(separator.r.findAllIn(row._1.mkString).length), row._2))
      .toDF("features", "rownumber")
    (sparkContext, separatorOcc)
  }

  def findPreambleByMedian(dataframe: DataFrame, separator: String): DataFrame ={

    val (sparkContext: SparkSession, separatorOcc: DataFrame) = createBuilderAndFindSeparator(dataframe, separator)

    val kmeans = new KMeans().setK(2).setSeed(1L)
    val model = kmeans.fit(separatorOcc)

    val predictions = model.transform(separatorOcc)

    val maxCluster = predictions
      .groupBy("prediction")
      .count()
      .collect
      .maxBy(row => row.getLong(1))

    val clusterScore = maxCluster.size
    if (clusterScore % 2 == 0) {
      val l = clusterScore / 2 - 1
      val r = l + 1
      (maxCluster(l) + maxCluster(r).toString).toDouble / 2
    } else
      maxCluster(clusterScore / 2).toString.toDouble

    null


  }



  // ------------------------------------------------------------- OLD CODE --------------------------------------------


  def removePreambleRecursive(dataFrame: DataFrame, line: Int): DataFrame = {
    if (line <= 0) {
      return dataFrame
    }
    else {
      var filteredDataset = removeFirstLine(dataFrame)

      removePreambleRecursive(filteredDataset, line - 1)
    }
  }

  //removes  first row and its uplicates
  def removeFirstLine(dataFrame: DataFrame): DataFrame = {
    val rdd = dataFrame.rdd.mapPartitionsWithIndex {
      case (index, iterator) => if (index == 0) iterator.drop(1) else iterator
    }
    val spark = SparkSession.builder.master("local").getOrCreate()
    spark.createDataFrame(rdd, dataFrame.schema)
  }


  def removePreamble(dataFrame: DataFrame): DataFrame = {
    val row = findRow(dataFrame)
    val erg = removePreambleRecursive(dataFrame, row)
    erg
  }

  def numberOfIdenticalRowsRemoved(dataFrame: DataFrame): Integer = {

    val skipable_first_row = dataFrame.first()
    val filteredDataset = dataFrame.filter(row => row == skipable_first_row)

    val erg = filteredDataset.count()
    erg.toInt
  }

  //removes all rows up to specified index
  def removePreambleOfFile(file: RDD[Row], rows: Integer): RDD[Row] = {

    val filteredTextFile = file.mapPartitionsWithIndex { (idx, iter) =>
      if (idx == 0) {
        iter.drop(rows)
      } else {
        iter
      }
    }
    filteredTextFile
  }

  //overwrite old header with first line of dataframe
  def newHeadForDataFrame(dataFrame: DataFrame, oldDataFrame: DataFrame): DataFrame = {
    val header = oldDataFrame.columns
    val firstHeader = header(0)
    if (firstHeader.matches(preambleCharacters)) {

      try {
        val newHeaderNames: Seq[String] = dataFrame.head().toSeq.asInstanceOf[Seq[String]]

        val newHeadedDataset = removeFirstLine(dataFrame.toDF(newHeaderNames: _*))
        newHeadedDataset
      } catch {
        case e: Exception =>
          return dataFrame.toDF("_c0");
      }

    }
    else {
      dataFrame
    }
  }

  //checks if the premable contains one of the preamble characters as first character

  def hasPreambleInColumn(dataFrame: DataFrame): Boolean = {
    if (dataFrame.columns(0).matches(preambleCharacters)) true
    else false
  }

  //rekursive search for first row which is not preamble like
  def findRow(dataFrame: DataFrame): Integer = {
    try {
      if (dataFrame.first().size <= 0) return 0
    } catch {
      case e: Exception => return 0;
    }
    val firstString = dataFrame.first().get(0).toString()

    val rows = 1
    if (firstString.matches(preambleCharacters)) {
      val reducedFrame = removeFirstLine(dataFrame)
      findRow(reducedFrame) + rows
    }
    else if (firstString.equals("")) {
      val reducedFrame = removeFirstLine(dataFrame)
      findRow(reducedFrame) + rows
    }
    //check for "-character as first character
    else if (firstString.matches("['\"].*$")) {
      val reducedFrame = removeFirstLine(dataFrame)
      findRow(reducedFrame) + rows
    }
    // else recursive abbort
    else
      0
  }

  /**
    * The abstract class of preparator implementation.
    *
    * @param abstractPreparator is the instance of { @link AbstractPreparator}. It needs to be converted to the corresponding subclass in the implementation body.
    * @param dataFrame        contains the intermediate dataset
    * @param errorAccumulator is the { @link CollectionAccumulator} to store preparation errors while executing the preparator.
    * @return an instance of { @link ExecutionContext} that includes the new dataset, and produced errors.
    * @throws Exception
    */
}
