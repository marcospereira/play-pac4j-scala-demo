package modules

import controllers._
import org.pac4j.play.filters.SecurityFilter
import play.api.http.HttpErrorHandler
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}

import router.Routes

class MacwireApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {

    // Set up logger
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }

    new MacwireApplicationComponents(context).application
  }
}

class MacwireApplicationComponents(context: ApplicationLoader.Context) extends BuiltInComponentsFromContext(context) with Pac4JSecurityComponents with AssetsComponents {

  import com.softwaremill.macwire._

  // Overrides the default error handler with a custom one.
  override lazy val httpErrorHandler: HttpErrorHandler = wire[CustomErrorHandler]

  // Here we cannot use Macwire again because there are two instances of
  // LogoutController available:
  // 1. logoutController
  // 2. centralLogoutController
  //
  // We can break the hierarchy between these two types and extract the common
  // code to somewhere else.
  override lazy val router: Router = new Routes(
    httpErrorHandler,
    applicationController,
    applicationWithFilter,
    applicationWithScalaHelper,
    callbackController,
    logoutController,
    centralLogoutController,
    assets
  )

  lazy val applicationController: controllers.Application = wire[controllers.Application]
  lazy val applicationWithFilter: ApplicationWithFilter = wire[ApplicationWithFilter]
  lazy val applicationWithScalaHelper: ApplicationWithScalaHelper = wire[ApplicationWithScalaHelper]

  // Wiring PAC4J security filters.
  lazy val securityFilter: SecurityFilter = wire[SecurityFilter]
  override def httpFilters: Seq[EssentialFilter] = wire[filters.Filters].filters
}