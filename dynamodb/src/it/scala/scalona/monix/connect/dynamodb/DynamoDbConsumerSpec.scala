package scalona.monix.connect.dynamodb

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{ Consumer, Observable }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.concurrent.ScalaFutures._
import software.amazon.awssdk.services.dynamodb.model._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

class DynamoDbConsumerSpec
  extends AnyWordSpecLike with Matchers with ScalaFutures with DynamoDbFixture with BeforeAndAfterAll {

  implicit val defaultConfig: PatienceConfig = PatienceConfig(10.seconds, 100.milliseconds)
  implicit val client: DynamoDbAsyncClient = DynamoDbClient()

  s"${DynamoDb}.transformer() " should {

    s"create a reactive Transformer" that {

      s"consumes a single `CreateTableRequest` and materializes to `CreateTableResponse`" in {
        //given
        val consumer: Consumer[CreateTableRequest, Task[CreateTableResponse]] =
          DynamoDb.consumer[CreateTableRequest, CreateTableResponse]
        val request =
          createTableRequest(tableName = tableName, schema = keySchema, attributeDefinition = tableDefinition)

        //when
        val t: Task[CreateTableResponse] =
          Observable.fromIterable(Iterable(request)).consumeWith(consumer).runSyncUnsafe()

        //then
        whenReady(t.runToFuture) { response: CreateTableResponse =>
          response shouldBe a[CreateTableResponse]
          response.tableDescription().hasKeySchema shouldBe true
          response.tableDescription().hasAttributeDefinitions shouldBe true
          response.tableDescription().hasGlobalSecondaryIndexes shouldBe false
          response.tableDescription().hasReplicas shouldBe false
          response.tableDescription().tableName() shouldEqual tableName
          response.tableDescription().keySchema() should contain theSameElementsAs keySchema
          response.tableDescription().attributeDefinitions() should contain theSameElementsAs tableDefinition
        }
      }

      s"consumes a single `PutItemRequest` and materializes to `PutItemResponse` " in {
        //given
        val consumer: Consumer[PutItemRequest, Task[PutItemResponse]] =
          DynamoDb.consumer[PutItemRequest, PutItemResponse]
        val request: PutItemRequest = genPutItemRequest.sample.get

        //when
        val t: Task[PutItemResponse] =
          Observable.fromIterable(Iterable(request)).consumeWith(consumer).runSyncUnsafe()

        //then
        whenReady(t.runToFuture) { response =>
          response shouldBe a[PutItemResponse]
          response.attributes().asScala should contain theSameElementsAs request.item().asScala
        }
      }

      s"consumes multiples `PutItemRequests` and materializes to `PutItemResponse` " in {
        //given
        val consumer: Consumer[PutItemRequest, Task[PutItemResponse]] =
          DynamoDb.consumer[PutItemRequest, PutItemResponse]
        val requests: List[PutItemRequest] = genPutItemRequests.sample.get

        //when
        val t: Task[PutItemResponse] =
          Observable.fromIterable(requests).consumeWith(consumer).runSyncUnsafe()

        //then
        whenReady(t.runToFuture) { response =>
          response shouldBe a[PutItemResponse]
          response.attributes().asScala should contain theSameElementsAs requests.last.item().asScala
        }
      }

      s"consumes a single `GetItemRequest` and materializes to `GetItemResponse` " in {
        //given
        val city = "Barcelona"
        val citizenId = 11292
        val debt: Int = 1015
        client.putItem(putItemRequest(tableName, city, citizenId, debt)).asScala.futureValue

        val request: GetItemRequest = getItemRequest(tableName, city, citizenId)
        val consumer: Consumer[GetItemRequest, Task[GetItemResponse]] = DynamoDb.consumer

        //when
        val t: Task[GetItemResponse] =
          Observable.fromIterable(Iterable(request)).consumeWith(consumer).runSyncUnsafe()

        //then
        whenReady(t.runToFuture) { response =>
          response shouldBe a[GetItemResponse]
          response.hasItem shouldBe true
          response.item() should contain key "debt"
          response.item().values().asScala.head.n().toDouble shouldBe debt
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    deleteTable(tableName)
    createCitiesTable()
  }

  override def afterAll(): Unit = {
    deleteTable(tableName)
    super.afterAll()
  }
}
