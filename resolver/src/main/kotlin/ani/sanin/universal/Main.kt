package ani.sanin.universal
import ani.sanin.universal.model.*
import ani.sanin.universal.server.startServer
import kotlinx.coroutines.runBlocking
fun main(a:Array<String>){if(a.firstOrNull()=="server"){startServer(a.getOrNull(1)?.toIntOrNull()?:8080);return};if(a.size<2){println("server [port] | <url> <title> [episode]");return};runBlocking{println(UniversalResolver().resolve(a[0],a[1],a.getOrNull(2)?.toIntOrNull()))}}
