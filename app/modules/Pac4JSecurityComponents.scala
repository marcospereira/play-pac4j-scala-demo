package modules

import java.io.File

import com.softwaremill.macwire._
import controllers.custom.{CallbackController, CentralLogoutController, LogoutController}
import controllers.{CustomAuthorizer, DemoHttpActionAdapter, RoleAdminAuthGenerator}
import org.pac4j.cas.client.CasClient
import org.pac4j.cas.config.{CasConfiguration, CasProtocol}
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
import org.pac4j.core.client.Clients
import org.pac4j.core.client.direct.AnonymousClient
import org.pac4j.core.config.Config
import org.pac4j.core.credentials.UsernamePasswordCredentials
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.matching.PathMatcher
import org.pac4j.core.profile.CommonProfile
import org.pac4j.http.client.direct.{DirectBasicAuthClient, ParameterClient}
import org.pac4j.http.client.indirect.{FormClient, IndirectBasicAuthClient}
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator
import org.pac4j.oauth.client.{FacebookClient, TwitterClient}
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.play.store.PlayCacheSessionStore
import org.pac4j.saml.client.{SAML2Client, SAML2ClientConfiguration}
import play.api.cache.ehcache.EhCacheComponents
import play.api.mvc.{BodyParsers, ControllerComponents, PlayBodyParsers}
import play.cache.{AsyncCacheApi => JavaAsyncCacheApi, DefaultAsyncCacheApi => JavaDefaultAsyncCacheApi, SyncCacheApi => JavaSyncCacheApi}
import play.core.j.JavaContextComponents

// PAC4J components created from SecurityModule.
// We need EhCache components here since PAC4J PlaySessionStore requires
// a cache implementation.
trait Pac4JSecurityComponents extends EhCacheComponents {

  def playBodyParsers: PlayBodyParsers
  def controllerComponents: ControllerComponents
  def javaContextComponents: JavaContextComponents

  lazy val baseUrl: String = configuration.get[String]("baseUrl")

  // Java Cache APIs. The `defaultCacheApi` used to create javaAsyncCacheApi comes
  // from EhCacheComponents.
  lazy val javaAsyncCacheApi: JavaAsyncCacheApi = new JavaDefaultAsyncCacheApi(defaultCacheApi)
  lazy val javaSyncCacheApi: JavaSyncCacheApi = javaAsyncCacheApi.sync()

  // The Java cache APIs are wired here to PlayCacheSessionStore.
  lazy val playSessionStore: PlayCacheSessionStore = wire[PlayCacheSessionStore]

  // PAC4J requires an explicit body parser of the type BodyParsers.Default. I think
  // it could be BodyParser[AnyContent] so that we would be able to use the default
  // components provided by Play cake.
  lazy val scalaDefaultBodyParser: BodyParsers.Default = wire[BodyParsers.Default]

  // Some PAC4J components requires that these two are implicit
  implicit lazy val securityComponents: SecurityComponents = DefaultSecurityComponents(playSessionStore, pac4jConfig, scalaDefaultBodyParser, controllerComponents)
  implicit lazy val pac4jScalaTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile] = new Pac4jScalaTemplateHelper(playSessionStore)

  // We need to create this manually instead of using Macwire because
  // there are two ControllerComponents instances available:
  // 1. controllerComponents (from Play)
  // 2. securityComponents (from PAC4J)
  //
  // The same applies for logoutController and centralLogoutController below.
  lazy val callbackController: CallbackController = new CallbackController(
    controllerComponents = controllerComponents,
    javaContextComponents = javaContextComponents,
    config = pac4jConfig,
    playSessionStore = playSessionStore,
    defaultUrl = Some("/?defaulturlafterlogout"),
    multiProfile = true
  )

  lazy val logoutController: LogoutController = new LogoutController(
    controllerComponents = controllerComponents,
    javaContextComponents = javaContextComponents,
    config = pac4jConfig,
    playSessionStore = playSessionStore,
    defaultUrl = Some("/")
  )

  lazy val centralLogoutController: CentralLogoutController = new CentralLogoutController(
    controllerComponents = controllerComponents,
    javaContextComponents = javaContextComponents,
    config = pac4jConfig,
    playSessionStore = playSessionStore
  )

  // Nothing interesting below.
  // It is just a direct translation from Guice "provides" methods to
  // lazy vals that reference each other. At some points we should be
  // able to use Macwire to create these components, but I don't think
  // it is worth the struggle.

  val facebookClient: FacebookClient = {
    val fbId = configuration.getOptional[String]("fbId").get
    val fbSecret = configuration.getOptional[String]("fbSecret").get
    new FacebookClient(fbId, fbSecret)
  }

  val twitterClient: TwitterClient = new TwitterClient("HVSQGAw2XmiwcKOTvZFbQ", "FSiO9G9VRR4KCuksky0kgGuo8gAVndYymr4Nl7qc8AA")

  val authenticator: Authenticator[UsernamePasswordCredentials] = new SimpleTestUsernamePasswordAuthenticator()

  val formClient: FormClient = new FormClient(baseUrl + "/loginForm", authenticator)

  val indirectBasicAuthClient: IndirectBasicAuthClient = new IndirectBasicAuthClient(authenticator)

  val directBasicAuthClient: DirectBasicAuthClient = new DirectBasicAuthClient(authenticator)

  val casClient: CasClient = {
    val casConfiguration = new CasConfiguration("https://casserverpac4j.herokuapp.com/login")
    casConfiguration.setProtocol(CasProtocol.CAS20)
    new CasClient(casConfiguration)
  }

  val saml2Client: SAML2Client = {
    val cfg = new SAML2ClientConfiguration("resource:samlKeystore.jks", "pac4j-demo-passwd", "pac4j-demo-passwd", "resource:openidp-feide.xml")
    cfg.setMaximumAuthenticationLifetime(3600)
    cfg.setServiceProviderEntityId("urn:mace:saml:pac4j.org")
    cfg.setServiceProviderMetadataPath(new File("target", "sp-metadata.xml").getAbsolutePath)
    new SAML2Client(cfg)
  }

  val oidcClient: OidcClient[OidcProfile, OidcConfiguration] = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId("343992089165-i1es0qvej18asl33mvlbeq750i3ko32k.apps.googleusercontent.com")
    oidcConfiguration.setSecret("unXK_RSCbCXLTic2JACTiAo9")
    oidcConfiguration.setDiscoveryURI("https://accounts.google.com/.well-known/openid-configuration")
    oidcConfiguration.addCustomParam("prompt", "consent")
    val oidcClient = new OidcClient[OidcProfile, OidcConfiguration](oidcConfiguration)
    oidcClient.addAuthorizationGenerator(new RoleAdminAuthGenerator)
    oidcClient
  }

  val parameterClient: ParameterClient = {
    val jwtAuthenticator = new JwtAuthenticator()
    jwtAuthenticator.addSignatureConfiguration(new SecretSignatureConfiguration("12345678901234567890123456789012"))
    val parameterClient = new ParameterClient("token", jwtAuthenticator)
    parameterClient.setSupportGetRequest(true)
    parameterClient.setSupportPostRequest(false)
    parameterClient
  }

  val pac4jConfig: Config = {
    val clients = new Clients(baseUrl + "/callback", facebookClient, twitterClient, formClient,
      indirectBasicAuthClient, casClient, saml2Client, oidcClient, parameterClient, directBasicAuthClient,
      new AnonymousClient())

    val config = new Config(clients)
    config.addAuthorizer("admin", new RequireAnyRoleAuthorizer[Nothing]("ROLE_ADMIN"))
    config.addAuthorizer("custom", new CustomAuthorizer)
    config.addMatcher("excludedPath", new PathMatcher().excludeRegex("^/facebook/notprotected\\.html$"))
    config.setHttpActionAdapter(new DemoHttpActionAdapter())
    config
  }
}
