package com.lambdaminute.s3staticweb

import cats.data.{Kleisli, OptionT}
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import ciris._
import ciris.api.Id
import org.http4s.server.AuthMiddleware
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.syntax.kleisli._
import org.http4s.{AuthedService, Request, Response}

final case class BasicAuthConf(user: String, pass: String)
final case class BucketName(str: String)

object Main extends IOApp {

  private val authConf: Option[BasicAuthConf] =
    loadConfig(
      env[Option[String]](key = "BASIC_USER"),
      env[Option[String]](key = "BASIC_PASS")
    )((u, p) => (u, p).mapN(BasicAuthConf)).toOption.flatten

  private val bucketName: BucketName =
    loadConfig(env[String](key = "BUCKET_NAME"))(BucketName)
      .fold(errs => sys.error(errs.message), identity)

  private val auth: AuthMiddleware[IO, Unit] =
    BasicAuth[IO, Unit](
      realm = "realm",
      validate = creds =>
        IO(
          authConf.fold(Option(()))(
            auth =>
              (creds.username == auth.user && creds.password == auth.pass)
                .guard[Option]
                .as(())
          )
      )
    )

  private val s3Routes: AuthedService[Unit, IO] =
    new S3StaticwebRoutes[IO](bucketName).helloWorldRoutes

  private val authedRoutes: Kleisli[OptionT[IO, ?], Request[IO], Response[IO]] =
    auth(s3Routes)

  private val blazeBuilder: BlazeServerBuilder[IO] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(authedRoutes.orNotFound)

  def run(args: List[String]): IO[ExitCode] =
    blazeBuilder.serve.compile.lastOrError

}
