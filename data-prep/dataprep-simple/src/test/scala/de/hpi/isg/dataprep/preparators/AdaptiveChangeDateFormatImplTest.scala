package de.hpi.isg.dataprep.preparators

import java.text.ParseException
import java.util

import de.hpi.isg.dataprep.components.Preparation
import de.hpi.isg.dataprep.model.repository.ErrorRepository
import de.hpi.isg.dataprep.model.target.errorlog.{ErrorLog, PreparationErrorLog}
import de.hpi.isg.dataprep.model.target.system.AbstractPreparation
import de.hpi.isg.dataprep.preparators.define.AdaptiveChangeDateFormat
import de.hpi.isg.dataprep.selection.DataLoadingConfigScala
import de.hpi.isg.dataprep.util.DatePattern

/**
  *
  * @author Hendrik Rätz, Nils Strelow
  * @since 2018/12/03
  */
class AdaptiveChangeDateFormatImplTest extends DataLoadingConfigScala {

  override var resourcePath: String = "/dates.csv"

  "Dates" should "be formatted given a target format" in {
    val preparator = new AdaptiveChangeDateFormat("date", None, DatePattern.DatePatternEnum.DayMonthYear)

    val preparation: AbstractPreparation = new Preparation(preparator)
    pipeline.addPreparation(preparation)
    pipeline.executePipeline()

    pipeline.getDataset.show

    val errorLogs: util.List[ErrorLog] = new util.ArrayList[ErrorLog]
    errorLogs.add(
      new PreparationErrorLog(preparation, "1989-01-00",
        new ParseException("No unambiguous pattern found to parse date. Date might be corrupted.", -1)))

    // Can't be parsed because this date was a Monday
    errorLogs.add(
      new PreparationErrorLog(preparation, "Tuesday, 13 Dec 1999",
        new ParseException("No unambiguous pattern found to parse date. Date might be corrupted.", -1)))

    val errorRepository: ErrorRepository = new ErrorRepository(errorLogs)

    println(pipeline.getErrorRepository.getPrintedReady)

    pipeline.getErrorRepository should equal(errorRepository)
  }

  "Dates in dataset" should "be formatted given a target format" in {

  }
}