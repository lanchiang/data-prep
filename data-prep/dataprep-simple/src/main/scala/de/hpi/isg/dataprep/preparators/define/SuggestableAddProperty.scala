package de.hpi.isg.dataprep.preparators.define

import java.util

import de.hpi.isg.dataprep.model.repository.MetadataRepository
import de.hpi.isg.dataprep.model.target.objects.Metadata
import de.hpi.isg.dataprep.model.target.schema.SchemaMapping
import de.hpi.isg.dataprep.model.target.system.{AbstractPipeline, AbstractPreparator}
import de.hpi.isg.dataprep.model.target.system.AbstractPreparator.PreparatorTarget
import de.hpi.isg.dataprep.util.DataType.PropertyType
import org.apache.spark.sql.{Dataset, Row}

import collection.JavaConverters._

/**
  * Add property for preparator suggestion experiment
  *
  * @author Lan Jiang
  * @since 2019-04-08
  */
class SuggestableAddProperty extends AbstractPreparator {

  preparatorTarget = PreparatorTarget.COLUMN_BASED

  var targetPropertyName: String = _
  var targetType: PropertyType = _
  var position: Int = _
  var filling: Any = _

  def this(_targetPropertyName: String,
           _targetType: PropertyType,
           _position: Int,
           _filling: Any) {
    this()
    targetPropertyName = _targetPropertyName
    targetType = _targetType
    position = _position
    filling = _filling
  }

  def this(targetPropertyName: String, targetType: PropertyType) {
    this(targetPropertyName, targetType, SuggestableAddProperty.DEFAULT_POSITION, AddProperty.DEFAULT_VALUE(targetType))

  }

  def this(targetPropertyName: String, targetType: PropertyType, position: Int) {
    this(targetPropertyName, targetType, position, AddProperty.DEFAULT_VALUE(targetType))
  }

  override def buildMetadataSetup(): Unit = {

  }

  override def calApplicability(schemaMapping: SchemaMapping, dataset: Dataset[Row], targetMetadata: util.Collection[Metadata], pipeline: AbstractPipeline): Float = {
    ???
  }

  override def getAffectedProperties: util.List[String] = {
    List.empty[String].asJava
  }
}

object SuggestableAddProperty {

  private val DEFAULT_POSITION = 0

  val DEFAULT_PROPERTY_NAME = null

  val DEFAULT_FILLING = "0"

  val DEFAULT_VALUE = (targetType: PropertyType) => {
    targetType match {
      case PropertyType.INTEGER => 0
      case PropertyType.DOUBLE => 0.0
      case PropertyType.STRING => ""
      case PropertyType.DATE => ""
      // now the default is undetermined
      case _ => ""
    }
  }
}