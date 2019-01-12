package de.hpi.isg.dataprep.preparators

import java.util

import de.hpi.isg.dataprep.metadata.PropertyDatePattern
import de.hpi.isg.dataprep.model.target.objects.{ColumnMetadata, Metadata}
import de.hpi.isg.dataprep.model.target.schema.SchemaMapping
import de.hpi.isg.dataprep.preparators.define.AdaptiveChangeDateFormat
import de.hpi.isg.dataprep.util.DatePattern
import org.apache.spark.sql.functions.col

/**
  *
  * @author Hendrik Rätz, Nils Strelow
  * @since 2018/12/03
  */
class AdaptiveChangeDateFormatTest extends PreparatorScalaTest {

  override var testFileName = "dates_applicability.csv"

  /*"Date format" should "be changed given source and target format" in {
    val preparator = new AdaptiveChangeDateFormat("date", None, DatePattern.DatePatternEnum.DayMonthYear)

    val preparation: AbstractPreparation = new Preparation(preparator)
    pipeline.addPreparation(preparation)
    pipeline.executePipeline()

    val errorLogs: util.List[ErrorLog] = new util.ArrayList[ErrorLog]
    val errorLog: PreparationErrorLog = new PreparationErrorLog(preparation, "1989-01-00", new ParseException("No unambiguous pattern found to parse date. Date might be corrupted.", -1))
    errorLogs.add(errorLog)
    val errorRepository: ErrorRepository = new ErrorRepository(errorLogs)

    pipeline.getRawData.show()

    println(pipeline.getErrorRepository.getPrintedReady)

    Assert.assertEquals(errorRepository, pipeline.getErrorRepository)
  }*/

  "calApplicability on the date column" should "return a score of 0.5" in {
    val columnName = "date"

    val metadata = new util.ArrayList[Metadata]()

    val preparator = new AdaptiveChangeDateFormat(columnName, None, DatePattern.DatePatternEnum.DayMonthYear)
    preparator.calApplicability(new SchemaMapping, dataContext.getDataFrame.select(col(columnName)), metadata
    ) should equal(0.5)
  }

  "calApplicability on the id column" should "return a score of 0" in {
    val columnName = "id"

    val metadata = new util.ArrayList[Metadata]()

    val preparator = new AdaptiveChangeDateFormat(columnName, None, DatePattern.DatePatternEnum.DayMonthYear)
    preparator.calApplicability(new SchemaMapping, dataContext.getDataFrame.select(col(columnName)), metadata) should equal(0)
  }

  "calApplicability" should "return a score of 0 for a column with metadata of a previous date formatting" in {

    val columnName = "date"

    val dateMetadata = new PropertyDatePattern(
      DatePattern.DatePatternEnum.DayMonthYear,
      new ColumnMetadata(columnName)
    )

    val metadata = new util.ArrayList[Metadata]()
    metadata.add(dateMetadata)

    val preparator = new AdaptiveChangeDateFormat(columnName, None, DatePattern.DatePatternEnum.DayMonthYear)
    preparator.calApplicability(new SchemaMapping, dataContext.getDataFrame.select(col(columnName)), metadata) should equal (0)
  }
}