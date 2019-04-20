package com.lambdaminute.s3staticweb

import cats.Functor
import cats.effect.{Effect, Timer, _}
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import fs2.aws.s3.readS3File
import org.http4s.AuthedService
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`

import scala.concurrent.duration._

class S3StaticwebRoutes[F[_]: Effect: Timer: ContextShift: Functor](
    bucketName: BucketName
) {

  private val dsl = new Http4sDsl[F] {}
  import dsl._

  private val s3ClientBuilder: AmazonS3ClientBuilder =
    AmazonS3ClientBuilder
      .standard()
      .withRegion("eu-west-1")

  private val s3Client = s3ClientBuilder.build()
  private val seconds  = fs2.Stream.awakeEvery[F](1.second)

  private def getMimeType(key: String): F[String] =
    Sync[F].delay {
      println(key)
      s3Client.getObjectMetadata(bucketName.str, key).getContentType
    }

  def helloWorldRoutes: AuthedService[Unit, F] =
    AuthedService {
      case GET -> Root / "seconds" as _ =>
        Ok(seconds.map(_.toString))
      case GET -> path as _ =>
        println(s"${bucketName.str}/${path.toList.mkString("/")}")

        for {
          mimeType <- getMimeType(path.toList.mkString("/")).map(s => `Content-Type`.parse(s)).map(_.right.get)
          res <- Ok(
            readS3File(
              bucket = bucketName.str,
              key = path.toList.mkString("/"),
              blockingEC = scala.concurrent.ExecutionContext.Implicits.global
            ))
        } yield res.withContentType(mimeType)
    }

}
