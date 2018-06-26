package controllers.custom

import javax.inject.Inject
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultLogoutLogic, LogoutLogic}
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.util.CommonHelper._
import org.pac4j.play.PlayWebContext
import org.pac4j.play.store.PlaySessionStore
import play.api.mvc.{AnyContentAsFormUrlEncoded, BaseController, ControllerComponents, Request}
import play.core.j.{JavaContextComponents, JavaHelpers}
import play.mvc.Http.RequestBody
import play.mvc.{Result => JavaResult}

// See comments on CallbackController.
//
// Original Java Code:
// https://github.com/pac4j/play-pac4j/blob/play-pac4j-parent-6.0.0/shared/src/main/java/org/pac4j/play/LogoutController.java
class LogoutController @Inject()(
  val controllerComponents: ControllerComponents,
  javaContextComponents: JavaContextComponents,
  config: Config,
  playSessionStore: PlaySessionStore,
  logoutLogic: LogoutLogic[JavaResult, PlayWebContext] = new DefaultLogoutLogic[JavaResult, PlayWebContext](),
  defaultUrl: Option[String] = None,
  logoutUrlPattern: Option[String] = None,
  localLogout: Boolean = true,
  destroySession: Boolean = false,
  centralLogout: Boolean = false
) extends BaseController {

  def createPlayWebContext[A](implicit request: Request[A]): PlayWebContext = {

    import scala.collection.JavaConverters._

    request.body match {
      case content: AnyContentAsFormUrlEncoded =>
        val javaBodyContent = content.asFormUrlEncoded
          .getOrElse(Map.empty[String, Seq[String]])
          .map(field => field._1 -> field._2.toArray) // Make field values Java-friendly
          .asJava
        val javaBody = new RequestBody(javaBodyContent)
        val jRequest = Request(request, javaBody)
        val jContext = JavaHelpers.createJavaContext(jRequest, javaContextComponents)
        new PlayWebContext(jContext, playSessionStore)
      case _ =>
        new PlayWebContext(request, playSessionStore)
    }
  }

  def logout = Action { implicit  req =>
    assertNotNull("logoutLogic", logoutLogic)
    assertNotNull("config", config)

    val playWebContext: PlayWebContext = createPlayWebContext
    val httpActionAdapter: HttpActionAdapter[JavaResult, PlayWebContext] = config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[JavaResult, PlayWebContext]]

    logoutLogic.perform(
      playWebContext,
      config,
      httpActionAdapter,
      defaultUrl.orNull,
      logoutUrlPattern.orNull,
      localLogout,
      destroySession,
      centralLogout
    ).asScala()
  }
}
