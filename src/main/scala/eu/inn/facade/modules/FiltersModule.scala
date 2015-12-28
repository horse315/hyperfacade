package eu.inn.facade.modules

import com.typesafe.config.Config
import eu.inn.facade.filter.chain.{FilterChainFactory, FilterChainRamlFactory}
import eu.inn.facade.filter.model.Filter
import eu.inn.facade.filter.{NoOpFilter, PrivateResourceFilter}
import scala.collection.JavaConversions._
import scaldi.Module

class FiltersModule extends Module {

  bind [Filter]               identifiedBy 'noop              to new NoOpFilter
  bind [Filter]               identifiedBy 'private           to new PrivateResourceFilter

  bind [FilterChainFactory] identifiedBy 'ramlFilterChain     to new FilterChainRamlFactory

  def initOuterBindings: Unit = {
    val config = inject[Config]
    if (config.hasPath("inn.facade.raml.filters")) {
      val declaredRamlFilters = config.getConfig("inn.facade.raml.filters")
      declaredRamlFilters.entrySet.foreach { filterConfigEntry ⇒
        val filterName = filterConfigEntry.getKey
        val filterClass: String = filterConfigEntry.getValue.unwrapped.asInstanceOf[String]
        bind[Filter] identifiedBy filterName to Class.forName(filterClass).asInstanceOf[Filter]
      }
    }
  }
}
