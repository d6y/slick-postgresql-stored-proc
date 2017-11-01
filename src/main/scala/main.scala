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

  // Create the schema and populate it with data (once)
  val initDb = 
    messages.schema.create.asTry >> (
      messages.length.result flatMap {
        case 0 => messages ++= freshTestData
        case _ => DBIO.successful(0)
      }
    )

  // Set up a few stored functions...

  // NB: $$$$ means $$ to Postgresql because $ needs to escaped as $$ in the string interpolator:
  val createSelectProc =
    sqlu"""
    CREATE OR REPLACE FUNCTION numMessages(name varchar) RETURNS int
    AS $$$$
    DECLARE result int;
    BEGIN
      SELECT COUNT(*) INTO STRICT result FROM message WHERE sender = name;
      RETURN result;
    END;
    $$$$ LANGUAGE plpgsql;
    """

  // An example of inserting something with (sort of) no results.
  // NB: this is not a true proceudre as it does return something. E.g.,
  // select systemMessage('hi');
  // systemmessage
  // ---------------
  //
  // (1 row)
  //
  // WIP: to run this we treat this "result" as a String
  // Probably best practice in this particular example would be to return
  // the number of rows inserted. (But we'd still want a void example)
  val createInsertProc =
    sqlu"""
    CREATE OR REPLACE FUNCTION systemMessage(msg text) RETURNS void
    AS $$$$
    BEGIN
      INSERT INTO message ("sender", "content") VALUES ('SYSTEM', msg);
    END;
    $$$$ LANGUAGE plpgsql;
    """

  // Plain SQL example...

  val program = for {
    _     <- initDb
    _     <- createSelectProc
    _     <- createInsertProc
    // Example procedure call which returns a value:
    name = "Dave"
    count <- sql"""select numMessages($name)""".as[Int]
    // Example call that does not return a value:
    msg = s"Counted $count messages for $name"
     _    <- sql"""select systemMessage($msg)""".as[String] // String? See comment above on the proc definition
  } yield count

  val db = Database.forConfig("example")
  try
    Await.result(db.run(program), 2.seconds).foreach(println)
  finally
    db.close

}
