package loader

import _root_.controllers.{AssetsComponents, CustomErrorHandler}
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.i18n.I18nComponents
import play.api.routing.Router
import com.softwaremill.macwire._
import org.pac4j.play.scala.{DefaultSecurityComponents, SecurityComponents}
import play.api.http.HttpErrorHandler
import play.api.mvc.BodyParsers
import router.Routes

class MacwireApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = new MacwireComponents(context).application
}

class MacwireComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with Pac4JComponents
  with ControllersComponents
  with AssetsComponents
  with I18nComponents
  with FiltersComponents {

  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment, context.initialConfiguration, Map.empty)
  }

  // This is needed because Pac4J DefaultSecurityComponents injects a BodyParser.Default
  lazy val wiredDefaultBodyParser:BodyParsers.Default = new BodyParsers.Default(playBodyParsers)

  lazy val securityComponents: SecurityComponents = DefaultSecurityComponents(
    playSessionStore, pac4JConfig, wiredDefaultBodyParser, controllerComponents
  )

  override lazy val httpErrorHandler: HttpErrorHandler = wire[CustomErrorHandler]

  override lazy val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/"
    wire[Routes]
  }
}
