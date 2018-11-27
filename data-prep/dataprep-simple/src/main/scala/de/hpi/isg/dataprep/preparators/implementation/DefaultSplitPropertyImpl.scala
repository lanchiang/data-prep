package de.hpi.isg.dataprep.preparators.implementation

import de.hpi.isg.dataprep.ExecutionContext
import de.hpi.isg.dataprep.components.PreparatorImpl
import de.hpi.isg.dataprep.model.error.PreparationError
import de.hpi.isg.dataprep.model.target.system.AbstractPreparator
import de.hpi.isg.dataprep.preparators.define.SplitProperty
import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.util.CollectionAccumulator

class DefaultSplitPropertyImpl extends PreparatorImpl {

  override def executeLogic(abstractPreparator: AbstractPreparator, dataFrame: Dataset[Row], errorAccumulator: CollectionAccumulator[PreparationError]): ExecutionContext = {
    val preparator = abstractPreparator.asInstanceOf[SplitProperty]
    val propertyName = preparator.propertyName
    val fromLeft = preparator.fromLeft

    if (!dataFrame.columns.contains(propertyName))
      throw new IllegalArgumentException(s"dataFrame has no column $propertyName")

    val separator = preparator.separator match {
      case null => findSeperator(dataFrame, propertyName)
      case Some(s) => s
    }

    val times = preparator.times match {
      case null => findMaxSeperatorCount(dataFrame, propertyName, separator)
      case Some(t) => t
    }

    if (times < 2)
      return new ExecutionContext(dataFrame, errorAccumulator)

    val splitValuesDataFrame = createSplitValuesDataFrame(dataFrame, propertyName, separator, fromLeft, times)

    splitValuesDataFrame.count()
    new ExecutionContext(splitValuesDataFrame, errorAccumulator)
  }

  def appendEmptyColumns(dataSet: Dataset[Row], propertyName: String, n: Int): Dataset[Row] = {
    var result = dataSet
    for (i <- 1 to n) {
      result = result.withColumn(s"$propertyName$i", lit(""))
    }
    result
  }

  def createSplitValuesDataFrame(dataFrame: Dataset[Row], propertyName: String, separator: String, fromLeft: Boolean, times: Int): Dataset[Row] = {
    val rowEncoder = RowEncoder(appendEmptyColumns(dataFrame, propertyName, times).schema)

    val splitValue = (value: String) => {
      var split = value.split(separator)
      if (!fromLeft)
        split = split.reverse

      if (split.length <= times) {
        val fill = List.fill(times - split.length)("")
        split = split ++ fill
      }

      val head = split.slice(0, times - 1)
      var tail = split.slice(times - 1, split.length)
      if (!fromLeft) {
        tail = tail.reverse
      }
      head :+ tail.mkString(separator)
    }

    dataFrame.map(
      row => {
        val index = row.fieldIndex(propertyName)
        val value = row.getAs[String](index)
        val split = splitValue(value)
        Row.fromSeq(row.toSeq ++ split)
      }
    )(rowEncoder)
  }

  def findMaxSeperatorCount(dataSet: Dataset[Row], propertyName: String, separator: String): Int = {
    val counts = dataSet
      .select(propertyName)
      .collect()
      .map(row => {
        val operatedValue: String = row.getAs[String](0)
        operatedValue.split(separator).length
      })

    counts.max
  }

  def findSeperator(dataFrame: Dataset[Row], propertyName: String): String = {
    val charMaps = dataFrame.select(propertyName).collect().map(row => {
      val value = row.getAs[String](0)
      value.groupBy(identity).filter(c => !c._1.isLetterOrDigit).mapValues(_.length)
    })
    val chars = charMaps.flatMap(map => map.keys).distinct
    val checkSeparatorCondition = (char: Char) => {
      val counts = charMaps.map(map => map.withDefaultValue(0)(char)).filter(x => x > 0)
      (counts.forall(_ == counts.head), counts.head, char)

    }
    chars.map(checkSeparatorCondition).filter(x => x._1).maxBy(x => x._2)._3.toString
  }
}
