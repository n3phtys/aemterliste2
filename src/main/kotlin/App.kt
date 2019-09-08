package aemterliste2

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.javalin.Javalin
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.CompletableFuture

val baseTextDir = System.getenv("AEMTERLISTE_TXT_FILE_BASE_DIR")?.let { if (it.isBlank()) null else it }?: File("./testdata").absolutePath

fun main() {
    println("Using baseTextDir: $baseTextDir")
    TxtFiles.values().forEach { println("Loaded:\n"+ runBlocking { it.loadContent()}) }
    println(runBlocking { TxtFiles.ElectedUserJson.parseToUsers() } )
    val app = Javalin.create().start(8080)
    app.get("/") { ctx ->
        run {
            ctx.contentType("text/html")
            ctx.result(generateHTMLFuture())
        }
    }
}




data class ElectedUser(val jobTitle: String,
                       val email: String,
                       val firstName: String,
                       val surName: String,
                       val nickName: String,
                       val reelectionDate: String
)



enum  class TxtFiles(val filename: String) {
    ActiveRedirections("mails.txt"),
    MailmanLists("mailmanmails.txt"), JobRedirections("aemtermails.txt"), ElectedUserJson("aemter.json"), SecondaryElectionJson("aemter27.json");

    suspend fun loadContent() : String {
        val remoteFile = File(baseTextDir+File.separator+this.filename)
        //TODO: cache files in local filesystem to deal with remote connection problems. May also cache in jvm if useful
        return remoteFile.readText(Charsets.UTF_8)
    }

    suspend fun parseToUsers(): List<ElectedUser> {
        val secondaryFile : TxtFiles = TxtFiles.SecondaryElectionJson
        val firstFile = this.loadContent()
        val secondFile = secondaryFile.loadContent()
        val gson = JsonParser()
        val perJob = mutableMapOf<String, MutableList<ElectedUser>>()
        val root = gson.parse(firstFile).asJsonObject
        val x =  root.entrySet().toList().map {
            val v = it.value.asJsonObject["DATENSATZ"].asJsonObject
            ElectedUser(jobTitle = v["AMT"].asString, email = v["E-MAIL"].asString, firstName = v["VORNAME-PRIVATPERSON"].asString, nickName = v["BIERNAME"].asString, surName = v["NACHNAME-PRIVATPERSON"].asString, reelectionDate = v["NEUWAHL"].asString + " " + v["JAHR"].asString)
        }
        x.forEach {perJob.computeIfAbsent(it.jobTitle) { mutableListOf() }.add(it)}


        gson.parse(secondFile).asJsonObject.entrySet().map { it.value.asJsonObject["DATENSATZ"].asJsonObject["AMT"].asString }.forEach { if(!perJob.containsKey(it)) {perJob[it] = mutableListOf(ElectedUser(jobTitle = it, email = "", firstName = "vakant", surName = "", nickName = "", reelectionDate = "N/A"))}}


        return perJob.toList().sortedBy { it.first }.flatMap{ pair -> pair.second.toList().sortedBy { it.firstName }}

    }

}


val reservierungenLinks = listOf("Gästezimmer Karlsruhe (Waldhornstraße)" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=gaestezimmer_ka", "Saal Karlsruhe" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=saal_ka", "Skihütte" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=skihuette", "Gästezimmer Berlin (Carmerstraße)" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=carmerstrasze")
val sonstigeLinks = listOf("Webseite" to "https://www.av-huette.de/", "SEWOBE Mitgliederportal" to "https://server30.der-moderne-verein.de/portal/index.php", "SEWOBE Ämterportal (nur für relevante Amtsträger)" to "https://server30.der-moderne-verein.de/module/login.php")

suspend fun generateHTML() : String {
    val output: Appendable = StringBuilder()
    run {
        output.appendHTML().html {
            head {
                meta {
                    charset = "utf-8"
                }
                meta {
                    name = "viewport"
                    content = "width=device-width, initial-scale=1, maximum-scale=1"
                }
                link {
                    rel = "stylesheet"
                    href = "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css"
                }
                title { +"""AVH Portal""" }

                style {
                    +"""pre {
                                white-space: pre-wrap; overflow-x: scroll;
                                }
                            """.trimIndent()
                }
            }
            body {
                div(classes = "container") {
                    div {
                        h1 { +"AVH Portal" }
                        p { +"Willkommen auf dem Selbstbedienungsportal des Akademischen Verein Hütte" }
                        h2 { +"Reservierungen" }
                        ul { reservierungenLinks.forEach { li { a(href = it.second) { +it.first } } } }
                        h2 { +"Sonstige Links" }
                        ul { sonstigeLinks.forEach { li { a(href = it.second) { +it.first } } } }
                        h2 { +"Ämterliste" }
                        p { +"Diese Liste wird auf Basis der SEWOBE-Datenbank jede Nacht neu erstellt. Unbesetzte Ämter werden nicht angezeigt." }

                        div {
                            div(classes = "table-responsive") {
                                table(classes = "table") {
                                    thead {
                                        tr {
                                            th { +"Amt" }
                                            th { +"Amtsträger" }
                                            th { +"Neuwahl" }
                                        }
                                    }
                                    tbody {
                                        val users = runBlocking { TxtFiles.ElectedUserJson.parseToUsers() }
                                        users.forEach { user ->
                                            tr {
                                                td { +user.jobTitle }
                                                td {
                                                    a(
                                                        href = user.email,
                                                        target = "_top"
                                                    ) { +"""${user.firstName} (${user.nickName}) ${user.surName}""" }
                                                }
                                                td { +user.reelectionDate }
                                            }
                                        }

                                    }
                                }
                            }
                        }
                        hr {}
                        h2 { +"Mailinglisten / aktive Weiterleitungen" }
                        p { +"""Dies sind die aktiven Mail-Weiterleitungen auf dem av-huette-Mailserver. Diese Liste ist im Format "x:y" wobei alle Mails an "x@av-huette.de" an Adresse "y" weitergeleitet werden. Diese Liste wird jeden Tag um 2 Uhr nachts automatisch neu generiert auf Basis der SEWOBE Datenbank.""" }
                        pre { +runBlocking { TxtFiles.ActiveRedirections.loadContent() } }

                        h2 { +"Mailadressen der Ämter" }
                        p { +"""Dies sind die aktiven Mail-Weiterleitungen der Ämter auf dem av-huette-Mailserver. Diese Liste ist im Format "x:y" wobei alle Mails an "x@av-huette.de" an Adresse "y" weitergeleitet werden. Diese Liste wird jeden Tag um 2 Uhr nachts automatisch neu generiert auf Basis der SEWOBE Datenbank.""" }
                        pre { +runBlocking { TxtFiles.JobRedirections.loadContent() } }

                        h2 { +"Aktive Mailman-Verteiler" }
                        p { +"""Dies sind die aktiven Mailman-Verteilerlisten ( = was für Verteiler gibt es überhaupt) auf dem av-huette-Mailserver. Diese Liste ist im Format "x:y" wobei alle Mails an "x@av-huette.de" an Adresse "y" weitergeleitet werden.""" }
                        pre { +runBlocking { TxtFiles.MailmanLists.loadContent() } }

                    }
                }
            }
        }
        return output.toString()
    }
}




    fun <V> suspendToFuture(function: suspend () -> V): CompletableFuture<V> {
        val value = CompletableFuture<V>()
        GlobalScope.launch {
            try {
                val v = function.invoke()
                value.complete(v)
            } catch (e : Throwable) {
                value.completeExceptionally(e)
            }
        }
        return value
    }



fun generateHTMLFuture() : CompletableFuture<String> = suspendToFuture {
    generateHTML()
}
