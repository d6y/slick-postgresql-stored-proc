import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Example extends App {

  final case class Message(
    sender:  String,
    content: Option[String],
    id:      Long = 0L)

  def freshTestData = Seq(
    Message("Dave", Some("Hello, HAL. Do you read me, HAL?")),
    Message("HAL",  Some("Affirmative, Dave. I read you.")),
    Message("Dave", Some("Open the pod bay doors, HAL.")),
    Message("HAL",  Some("I'm sorry, Dave. I'm afraid I can't do that.")),
    Message("Dave", None)
  )

  final class MessageTable(tag: Tag) extends Table[Message](tag, "message") {

    def id      = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def sender  = column[String]("sender")
    def content = column[Option[String]]("content")

    def * = (sender, content, id).mapTo[Message]
  }

  lazy val messages = TableQuery[MessageTable]

  val initDb = 
    messages.schema.create.asTry >> (
      messages.length.result flatMap {
        case 0 => messages ++= freshTestData
        case _ => DBIO.successful(0)
      }
    )

  val program = for {
    _       <- initDb
    senders <- messages.filter(m => m.sender like "%Dave%").result
  } yield senders

  val db = Database.forConfig("example")
  try
    Await.result(db.run(program), 2.seconds).foreach(println)
  finally
    db.close

}
