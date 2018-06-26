package controllers.custom

import javax.inject.Inject
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultLogoutLogic, LogoutLogic}
import org.pac4j.play.PlayWebContext
import org.pac4j.play.store.PlaySessionStore
import play.api.mvc.ControllerComponents
import play.core.j.JavaContextComponents

import play.mvc.{Result => JavaResult}

// This only pre-configures some LogoutController parameters. For example, the value for
// centralLogout is true instead of the default false. To be honest, I don't understand why
// do we need two different logout controllers.
//
// Original Java Code:
// https://github.com/pac4j/play-pac4j-scala-demo/blob/c7ed5b980075aca22c0053dc6605d85e87d8ced7/app/controllers/CentralLogoutController.java
class CentralLogoutController @Inject()(
  controllerComponents: ControllerComponents,
  javaContextComponents: JavaContextComponents,
  config: Config,
  playSessionStore: PlaySessionStore,
  logoutLogic: LogoutLogic[JavaResult, PlayWebContext] = new DefaultLogoutLogic[JavaResult, PlayWebContext]()
) extends LogoutController(
  controllerComponents,
  javaContextComponents,
  config,
  playSessionStore,
  logoutLogic,
  Some("http://localhost:9000/?defaulturlafterlogoutafteridp"),
  Some("http://localhost:9000/.*"),
  false,
  false,
  true
)
