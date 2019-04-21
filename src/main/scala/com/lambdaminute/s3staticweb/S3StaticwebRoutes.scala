package com.lambdaminute.s3staticweb

import java.nio.charset.StandardCharsets

import cats.Functor
import cats.data.EitherT
import cats.effect.{Effect, Timer, _}
import cats.implicits._
import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{AmazonS3Exception, ListObjectsRequest}
import fs2.aws.s3.readS3File
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{AuthedService, MediaType, ParseFailure, Response, Status}
import scalatags.Text
import software.amazon.awssdk.awscore.exception.AwsServiceException
import scalatags.Text.all._

import scala.collection.JavaConverters._
sealed trait S3Error
final case class MimeParseError(err: ParseFailure) extends S3Error
final case class S3ServiceError(err: AwsServiceException) extends S3Error
final case class S3GenericError(err: Throwable) extends S3Error

class S3StaticwebRoutes[F[_]: Effect: Timer: ContextShift: Functor](
  s3Conf: S3Conf
) {

  private val dsl = new Http4sDsl[F] {}
  import dsl._

  private val s3ClientBuilder: AmazonS3ClientBuilder =
    AmazonS3ClientBuilder
      .standard()
      .withRegion(s3Conf.region)

  private val s3Client = s3ClientBuilder.build()

  private def dirListingHtml(list: List[String]): Text.TypedTag[String] = html(
    body(ul(list.map(s => li(a(s, href := s)))))
  )

  private def getMimeType(key: String): EitherT[F, S3Error, `Content-Type`] =
    EitherT(
      Sync[F]
        .delay {
          `Content-Type`
            .parse(
              s3Client.getObjectMetadata(s3Conf.bucketName, key).getContentType
            )
            .leftMap[S3Error](MimeParseError)
        }
        .recover {
          case err: AmazonS3Exception
              if err.getErrorCode() == "404 Not Found" =>
            Either.right(`Content-Type`(MediaType.text.html))
          case err: AwsServiceException =>
            Either.left(S3ServiceError(err))
          case err: SdkClientException => Either.left(S3GenericError(err))
        }
    )

  private def listDir(path: Path): fs2.Stream[F, Byte] =
    fs2.Stream.emits {
      val _ = path
      val prefix = path.toList.mkString("/")

      val request = s3Client.listObjects(
        new ListObjectsRequest()
          .withBucketName(s3Conf.bucketName)
          .withDelimiter("/")
          .withPrefix(prefix)
      )
      val paths =
        request.getCommonPrefixes.asScala.toList
          .map(str => s"${str}index.html".drop(prefix.length))
      val rendered = dirListingHtml(paths).render
      rendered
        .getBytes(StandardCharsets.UTF_8)
    }

  def helloWorldRoutes: AuthedService[Unit, F] =
    AuthedService {
      case GET -> path as _ =>
        println(s"${s3Conf.bucketName}/${path.toList.mkString("/")}")

        val isDir = path.toList
          .mkString("/")
          .endsWith("/") || path.toList.isEmpty

        val r: EitherT[F, S3Error, Response[F]] = for {
          mimeType <- getMimeType(path.toList.mkString("/"))
          _ = println(mimeType)
          res <- EitherT.right(
            Ok(
              if (isDir)
                listDir(path)
              else
                readS3File(
                  bucket = s3Conf.bucketName,
                  key =
                    if (path.toList.isEmpty) "index.html"
                    else path.toList.mkString("/"),
                  blockingEC =
                    scala.concurrent.ExecutionContext.Implicits.global
                )
            )
          )
        } yield res.withContentType(mimeType)
        r.fold(
          {
            case MimeParseError(err) =>
              new Response(Status.InternalServerError)
                .withEntity(err.getMessage())
            case S3GenericError(err) =>
              new Response(Status.InternalServerError)
                .withEntity(err.getMessage())
            case S3ServiceError(err) =>
              new Response(
                status = Status.apply(err.awsErrorDetails().errorCode().toInt)
              )
          },
          identity
        )
    }

}
