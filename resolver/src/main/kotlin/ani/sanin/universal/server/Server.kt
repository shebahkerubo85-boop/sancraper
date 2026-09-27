package ani.sanin.universal.server

import ani.sanin.universal.*
import ani.sanin.universal.model.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun startServer(port:Int=8080){ embeddedServer(Netty,port=port,host="0.0.0.0"){ install(ContentNegotiation){json(Json{classDiscriminator="type";ignoreUnknownKeys=true})}; val resolver=UniversalResolver(); routing {
 get("/health"){call.respond(mapOf("ok" to true,"service" to "UniversalAnimeResolver","version" to "3"))}
 get("/v1/capabilities"){call.respond(mapOf("discovery" to true,"episodeResolution" to true,"browserObservation" to true,"mediaTypes" to listOf("MP4","WebM","HLS","DASH"),"drmBypass" to false))}
 post("/v1/resolve"){val q=runCatching{call.receive<ResolveRequest>()}.getOrNull()?:return@post call.respondText("Invalid JSON",status=io.ktor.http.HttpStatusCode.BadRequest); val x=resolver.resolve(q.url,q.animeTitle,q.episode); call.respond(ApiResponse(x is ResolveResult.Success,x))}
 post("/v1/browser-observation"){val q=runCatching{call.receive<BrowserObservation>()}.getOrNull()?:return@post call.respondText("Invalid JSON",status=io.ktor.http.HttpStatusCode.BadRequest); val x=BrowserObservationResolver.resolve(q); call.respond(ApiResponse(x is ResolveResult.Success,x))}
 } }.start(wait=true)}
