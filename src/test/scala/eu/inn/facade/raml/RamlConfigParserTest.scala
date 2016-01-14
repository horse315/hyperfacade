package eu.inn.facade.raml

import java.nio.file.Paths

import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}
import eu.inn.facade.raml.Annotation._
import eu.inn.facade.raml.DataType._

class RamlConfigParserTest extends FreeSpec with Matchers {
  val ramlConfig = readConfig

  def readConfig:RamlConfig = {
    val config = ConfigFactory.load()
    val factory = new JavaNodeFactory
    val fileRelativePath = config.getString("inn.facade.raml.file")
    val fileUri = Thread.currentThread().getContextClassLoader.getResource(fileRelativePath).toURI
    val file = Paths.get(fileUri).toFile
    val api = factory.createApi(file.getCanonicalPath)
    RamlConfigParser(api).parseRaml
  }

  "RamlConfig" - {
    "traits" in {
      ramlConfig.traitNames("/status", "get") shouldBe Seq("rateLimited")
      ramlConfig.traitNames("/users", "get") shouldBe Seq("paged", "rateLimited", "secured")
      ramlConfig.traitNames("/users", "post") shouldBe Seq("secured")
      ramlConfig.traitNames("/status/test-service", "get") shouldBe Seq("paged")
      ramlConfig.traitNames("/private", "get") shouldBe Seq("privateResource")
    }

    "request data structure" in {
      val usersHeaders = Seq(Header("authToken"))
      val usersBody = Body(DataType("StatusRequest", Seq(Field("serviceType", DataType())), Seq()))
      ramlConfig.requestDataStructure("/users", "get") shouldBe Some(DataStructure(usersHeaders, Some(usersBody)))

      val testServiceHeaders = Seq(Header("authToken"))
      val testServiceBody = Body(
        DataType("TestRequest",
          Seq(Field("mode", DataType()),
              Field("resultType", DataType()),
              Field("clientIP", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(CLIENT_IP)))),
              Field("clientLanguage", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(CLIENT_LANGUAGE))))),
          Seq()))
      ramlConfig.requestDataStructure("/status/test-service", "get") shouldBe Some(DataStructure(testServiceHeaders, Some(testServiceBody)))
    }

    "response data structure" in {
      val usersHeaders = Seq(Header("content-type"))
      val usersBody = Body(
        DataType("Status",
          Seq(Field("statusCode", DataType("number", Seq(), Seq())),
              Field("processedBy", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(PRIVATE))))),
          Seq()))
      ramlConfig.responseDataStructure("/users", "get", 200, None) shouldBe Some(DataStructure(usersHeaders, Some(usersBody)))

      val testServiceHeaders = Seq(Header("content-type"))
      val testServiceBody = Body(
        DataType("Status",
          Seq(Field("statusCode", DataType("number", Seq(), Seq())),
              Field("processedBy", DataType(DEFAULT_TYPE_NAME, Seq(), Seq(Annotation(PRIVATE))))),
          Seq()))
      ramlConfig.responseDataStructure("/status/test-service", "get", 200, None) shouldBe Some(DataStructure(testServiceHeaders, Some(testServiceBody)))

      val test404Headers = Seq[Header]()
      val test404Body = Body(DataType())
      ramlConfig.responseDataStructure("/status/test-service", "get", 404, None) shouldBe Some(DataStructure(test404Headers, Some(test404Body)))
    }

    "response data structures by contentType" in {
      val feedHeaders = Seq()
      val reliableResourceStateBody = Body(
        DataType("ReliableResourceState",
          Seq(Field("revisionId", DataType("number", Seq(), Seq())),
              Field("content", DataType())),
          Seq()))
      val reliableResourceUpdateBody = Body(
        DataType("ReliableResourceUpdate",
          Seq(Field("revisionId", DataType("number", Seq(), Seq())),
              Field("update", DataType())),
          Seq()))
      val resourceStateContentType = Some("application/vnd+app-server-status.json")
      val resourceUpdateContentType = Some("application/vnd+app-server-status-update.json")

      ramlConfig.responseDataStructure("/reliable-feed", "get", 200, resourceStateContentType) shouldBe Some(DataStructure(feedHeaders, Some(reliableResourceStateBody)))
      ramlConfig.responseDataStructure("/reliable-feed", "get", 200, resourceUpdateContentType) shouldBe Some(DataStructure(feedHeaders, Some(reliableResourceUpdateBody)))
    }
  }
}
