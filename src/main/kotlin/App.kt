package aemterliste2

import com.google.gson.JsonParser
import io.javalin.Javalin
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime



val baseTextDir: File =  File("testdata")

fun main() {
    println("Using baseTextDir: $baseTextDir")
    TxtFiles.values().forEach { println("Loaded:\n" + it.loadContent()) }
    println(TxtFiles.ElectedUserJson.parseToUsers())
    val app = Javalin.create().start(8080)
    app.get("/") { ctx ->
        run {
            ctx.contentType("text/html")
            ctx.result(generateHTMLFuture())
        }
    }
}


data class ElectedUser(
    val jobTitle: String,
    val email: String,
    val firstName: String,
    val surName: String,
    val nickName: String,
    val reelectionDate: String
)

private fun getSewobeQuery(): SewobeQuery? {
    val url = System.getenv("SEWOBEURL")
        ?: "https://server30.der-moderne-verein.de/restservice/standard/v1.0/auswertungen/get_auswertung_data.php"
    val un = System.getenv("SEWOBEUSER") ?: "restuser"
    val pw = System.getenv("SEWOBEPASSWORD") ?: null
    return if (pw != null) {
        SewobeQuery(url, un, pw)
    } else {
        null
    }
}

data class SewobeQuery(val url: String, val username: String, val password: String?) {
    fun query(sewobeQueryId: Int): String? {
        try {
            val urlObj = URL(url)
            with(urlObj.openConnection() as HttpURLConnection) {
                doOutput = true
                requestMethod = "POST"
                val myParameters = "USERNAME=$username&PASSWORT=$password&AUSWERTUNG_ID=$sewobeQueryId"
                val writer = OutputStreamWriter(outputStream)
                writer.write(myParameters)
                writer.flush()
                val s = inputStream.bufferedReader().readText()
                //check if actually valid json (useless otherwise)
                checkIfJson(s)
                return s
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

}


private fun checkIfJson(s: String) {
    require(null != JsonParser().parse(s))
}

private val durationBetweenCaching: Duration = Duration.ofMinutes(5)!!

enum class TxtFiles(private val filename: String, private val sewobeQueryId: Int? = null /*if set, queries sewobe*/) {
    ActiveRedirections("mails.txt"),
    MailmanLists("mailmanmails.txt"), JobRedirections("aemtermails.txt"), ElectedUserJson(
        "aemter.json",
        170
    ),
    SecondaryElectionJson("aemter27.json", 27);


    private var lastUpdate = LocalDateTime.of(0, 1, 1, 0, 0)
    private var lastValue: String = ""

    @Synchronized
    fun loadContent(): String {
        val now = LocalDateTime.now()
        if (lastUpdate.plus(durationBetweenCaching).isBefore(now)) {
            val remoteFile = baseTextDir.resolve(this.filename)
            val qid = this.sewobeQueryId
            val result: String = if (qid != null) {
                val q = getSewobeQuery()
                if (q != null) {
                    val r = q.query(qid)
                    if (r != null) {
                        //complex check to prevent writing if nothing changed
                        if (r != lastValue && (!(remoteFile.exists()) || remoteFile.readText(Charsets.UTF_8) != r)) {
                            remoteFile.writeText(r, Charsets.UTF_8)
                        }
                        r
                    } else {
                        remoteFile.readText(Charsets.UTF_8)
                    }
                } else {
                    remoteFile.readText(Charsets.UTF_8)
                }
            } else {
                remoteFile.readText(Charsets.UTF_8)
            }
            this.lastValue = result
            this.lastUpdate = LocalDateTime.now()
        }
        return lastValue
    }

    fun parseToUsers(): List<ElectedUser> {
        val secondaryFile: TxtFiles = SecondaryElectionJson
        val firstFile = this.loadContent()
        val secondFile = secondaryFile.loadContent()
        val gson = JsonParser()
        val perJob = mutableMapOf<String, MutableList<ElectedUser>>()
        val root = gson.parse(firstFile).asJsonObject
        val x = root.entrySet().toList().flatMap {
            val v = it.value.asJsonObject["DATENSATZ"].asJsonObject
            val aemter = v["AMT"].asString.split(',')
            aemter.map { amt ->
                ElectedUser(
                    jobTitle = amt,
                    email = v["E-MAIL"].asString,
                    firstName = v["VORNAME-PRIVATPERSON"].asString,
                    nickName = v["BIERNAME"].asString,
                    surName = v["NACHNAME-PRIVATPERSON"].asString,
                    reelectionDate = v["NEUWAHL"].asString + " " + v["JAHR"].asString
                )
            }
        }
        x.forEach { perJob.computeIfAbsent(it.jobTitle) { mutableListOf() }.add(it) }


        gson.parse(secondFile).asJsonObject.entrySet()
            .map { it.value.asJsonObject["DATENSATZ"].asJsonObject["AMT"].asString }.forEach {
                if (!perJob.containsKey(it)) {
                    perJob[it] = mutableListOf(
                        ElectedUser(
                            jobTitle = it,
                            email = "",
                            firstName = vakant,
                            surName = "",
                            nickName = "",
                            reelectionDate = "N/A"
                        )
                    )
                }
            }


        return perJob.toList().sortedBy { it.first }.flatMap { pair -> pair.second.toList().sortedBy { it.firstName } }

    }

}


val reservierungenLinks = listOf(
    "Gästezimmer Karlsruhe (Waldhornstraße)" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=gaestezimmer_ka",
    "Saal Karlsruhe" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=saal_ka",
    "Skihütte" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=skihuette",
    "Gästezimmer Berlin (Carmerstraße)" to "https://reservierungen.av-huette.de/v2/index.html?selected_key=carmerstrasze"
)
val sonstigeLinks = listOf(
    "Webseite" to "https://www.av-huette.de/",
    "SEWOBE Mitgliederportal" to "https://server30.der-moderne-verein.de/portal/index.php",
    "SEWOBE Ämterportal (nur für relevante Amtsträger)" to "https://server30.der-moderne-verein.de/module/login.php"
)

fun generateHTML(): String {
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
                    ("""pre {
                                white-space: pre-wrap; overflow-x: scroll;
                                }
                            """.trimIndent())
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
                                        val users = TxtFiles.ElectedUserJson.parseToUsers()
                                        users.forEach { user ->
                                            tr {
                                                td { +user.jobTitle }
                                                td {
                                                    if (user.firstName == vakant) {
                                                        +vakant
                                                    } else {
                                                        a(
                                                            href = user.email,
                                                            target = "_top"
                                                        ) { +"""${user.firstName} (${user.nickName}) ${user.surName}""" }
                                                    }
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
                        pre { +(TxtFiles.ActiveRedirections.loadContent()) }

                        h2 { +"Mailadressen der Ämter" }
                        p { +"""Dies sind die aktiven Mail-Weiterleitungen der Ämter auf dem av-huette-Mailserver. Diese Liste ist im Format "x:y" wobei alle Mails an "x@av-huette.de" an Adresse "y" weitergeleitet werden. Diese Liste wird jeden Tag um 2 Uhr nachts automatisch neu generiert auf Basis der SEWOBE Datenbank.""" }
                        pre { +(TxtFiles.JobRedirections.loadContent()) }

                        h2 { +"Aktive Mailman-Verteiler" }
                        p { +"""Dies sind die aktiven Mailman-Verteilerlisten ( = was für Verteiler gibt es überhaupt) auf dem av-huette-Mailserver. Diese Liste ist im Format "x:y" wobei alle Mails an "x@av-huette.de" an Adresse "y" weitergeleitet werden.""" }
                        pre { +(TxtFiles.MailmanLists.loadContent()) }

                    }
                }
            }
        }
        return output.toString()
    }
}

const val vakant = "vakant"


fun generateHTMLFuture(): String = generateHTML()
