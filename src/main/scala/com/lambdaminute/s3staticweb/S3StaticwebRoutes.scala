package com.lambdaminute.s3staticweb

import cats.effect.{Effect, Timer, _}
import fs2.aws.s3.readS3File
import org.http4s.AuthedService
import org.http4s.dsl.Http4sDsl

import scala.concurrent.duration._

class S3StaticwebRoutes[F[_]: Effect: Timer: ContextShift](
  bucketName: BucketName
) {

  private val dsl = new Http4sDsl[F] {}
  import dsl._

  private val seconds = fs2.Stream.awakeEvery[F](1.second)

  def helloWorldRoutes: AuthedService[Unit, F] =
    AuthedService {
      case GET -> Root / "seconds" as _ =>
        Ok(seconds.map(_.toString))
      case GET -> path as _ =>
        println(s"${bucketName.str}/${path.toList.mkString("/")}")

        Ok(
          readS3File(
            bucket = bucketName.str,
            key = path.toList.mkString("/"),
            blockingEC = scala.concurrent.ExecutionContext.Implicits.global
          )
        )
    }

}
