package controllers.custom

import javax.inject.Inject
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{CallbackLogic, DefaultCallbackLogic}
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.util.CommonHelper._
import org.pac4j.play.PlayWebContext
import org.pac4j.play.store.PlaySessionStore
import play.api.mvc.{AnyContentAsFormUrlEncoded, BaseController, ControllerComponents, Request}
import play.core.j.{JavaContextComponents, JavaHelpers}
import play.mvc.Http.RequestBody
import play.mvc.{Result => JavaResult}

// Implementing this in Scala so we won't mix Java controllers with Scala compile-time
// dependency injection. We are doing this because it would require deep knowledge of
// Play internal APIs to wire all the Java components together and make them available
// to Scala compile-time DI.
//
// Original Java Code:
// https://github.com/pac4j/play-pac4j/blob/play-pac4j-parent-6.0.0/shared/src/main/java/org/pac4j/play/CallbackController.java
class CallbackController @Inject()(
  val controllerComponents: ControllerComponents,
  javaContextComponents: JavaContextComponents,
  config: Config,
  playSessionStore: PlaySessionStore,
  // In the Guice example, this is not injected, but created inside the controller
  // when initializing. We then can have it injected if we want, but have a default
  // value that matches the Guice version.
  callbackLogic: CallbackLogic[JavaResult, PlayWebContext] = new DefaultCallbackLogic[JavaResult, PlayWebContext](),
  defaultUrl: Option[String] = None,
  defaultClient: Option[String] = None,
  multiProfile: Boolean = false,
  // the internals of pac4j will treat a null Boolean as true for
  // saveInSession. Since using nulls is not ideal in Scala, I've
  // put the default value as true already.
  saveInSession: Boolean = true
) extends BaseController {

  // This is one of the important integrations points between the Scala
  // API in Play and the Java API in PAC4J. PAC4J requires a PlayWebContext
  // that is created using either a Request (from Play Scala API) or an
  // Http.Context (from Play Java API). This method does this creation and
  // it is a copy-and-paste of what we have internally in PAC4J.
  def createPlayWebContext[A](request: Request[A]): PlayWebContext = {

    import scala.collection.JavaConverters._

    request.body match {
      // If it is a form submission, then convert the Scala body payload
      // to a Java body payload (which is just a Map<String, String[]>.
      // Then create a RequestBody (from Play Java API) and set it as the
      // request body that will be later used to create a Http.Context.
      //
      // Form submissions are used by client (browser) to perform logins.
      case content: AnyContentAsFormUrlEncoded =>
        val javaBodyContent = content.asFormUrlEncoded
          .getOrElse(Map.empty[String, Seq[String]])
          .map(field => field._1 -> field._2.toArray) // Make field values Java-friendly
          .asJava
        val javaBody = new RequestBody(javaBodyContent)
        val jRequest = Request(request, javaBody)
        val jContext = JavaHelpers.createJavaContext(jRequest, javaContextComponents)
        new PlayWebContext(jContext, playSessionStore)
      // If it is not a form submission, then just use the regular request
      // to create PAC4J PlayWebContext.
      case _ =>
        new PlayWebContext(request, playSessionStore)
    }

  }

  // This action is a direct translation from Java versions in PAC4J.
  // The only difference is that we now have the method above to create
  // a PlayWebContext.
  def callback = Action { implicit req =>

    assertNotNull("callbackLogic", callbackLogic)
    assertNotNull("config", config)

    val playWebContext: PlayWebContext = createPlayWebContext(req)
    val httpActionAdapter: HttpActionAdapter[JavaResult, PlayWebContext] = config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[JavaResult, PlayWebContext]]

    callbackLogic.perform(
      playWebContext,
      config,
      httpActionAdapter,
      defaultUrl.orNull,
      saveInSession,
      multiProfile,
      false,
      defaultClient.orNull
    ).asScala()
  }
}
