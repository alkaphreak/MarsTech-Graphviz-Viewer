package com.marstech.graphvizviewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler
import org.cef.browser.CefBrowser
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import androidx.compose.ui.awt.SwingPanel as ComposeSwingPanel

class DotViewerCli : CliktCommand() {

    // Do not preallocate a port. Let Ktor bind an ephemeral port (0) and read it back.
    // Make the DOT file argument optional; default to `docs/sample.dot` during development.
    private val dotFileArg: File? by argument(
        name = "dotFile",
        help = "Path to the DOT file (optional — defaults to docs/sample.dot for dev)"
    ).file(
        mustExist = true,
        canBeDir = false,
        mustBeReadable = true,
    ).optional()

    private val display: Boolean by option(
        names = arrayOf("--display", "-d"),
        help = "Display the contents of the DOT file",
    ).flag(default = false)

    override fun run() {
        // Resolve the DOT file: use provided argument or fall back to docs/sample.dot during dev
        val dotFile = dotFileArg ?: File("docs/sample.dot")
        if (!dotFile.exists()) {
            println("DOT file not found: ${dotFile.path}")
            return
        }

        if (display) {
            println(dotFile.readText())
            return
        }
        val dotContentRef: AtomicReference<String?> = AtomicReference(dotFile.readText())
        val dotChangedChannel: Channel<Unit> = Channel(Channel.BUFFERED)

        println("Starting server (requesting ephemeral port)")
        val server = embeddedServer(factory = Netty, port = 0) {
            routing {
                get("/viz-global.js") {
                    call.respondFile(File("src/main/resources/static/js/viz-global.js"))
                }
                get("/dot-events") {
                    call.response.cacheControl(CacheControl.NoCache(null))
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        while (true) {
                            dotChangedChannel.receive()
                            write("event: dotChanged\ndata: update\n\n")
                            flush()
                        }
                    }
                }
                get("/") {
                    val dotContent = dotFile.readText()
                    val safeDotContent = Json.encodeToString(
                        serializer = String.serializer(),
                        value = dotContent
                    )
                    println("Serving DOT content (length=${dotContent.length})")
                    println(dotContent)

                    call.respondText(
                        """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <title>Graphviz DOT Viewer</title>
                            <script src="/viz-global.js"></script>
                        </head>
                        <body>
                            <div id="graph"></div>
                            <script>
                                function render(dot) {
                                    if (typeof Viz === 'undefined') {
                                        document.getElementById('graph').innerText = 'Viz.js failed to load.';
                                        return;
                                    }
                                    Viz.instance().then(viz => {
                                        document.getElementById('graph').innerHTML = '';
                                        viz.renderSVGElement(dot).then(svg => {
                                            document.getElementById('graph').appendChild(svg);
                                        });
                                    });
                                }
                                const dot = $safeDotContent;
                                render(dot);
                                const evtSource = new EventSource('/dot-events');
                                evtSource.addEventListener('dotChanged', function(e) {
                                    fetch('/').then(r => r.text()).then(html => {
                                        const match = html.match(/const dot = ([^;]+);/);
                                        if (match) {
                                            const newDot = JSON.parse(match[1]);
                                            render(newDot);
                                        } else {
                                            location.reload();
                                        }
                                    });
                                });
                            </script>
                        </body>
                        </html>
                        """,
                        ContentType.Text.Html
                    )
                }
            }
        }.start(wait = false)

        // Read the actual bound port from the running server
        val actualPort = server.environment.connectors.firstOrNull()?.port
            ?: throw IllegalStateException("Failed to determine server port")
        println("Server started on port $actualPort")

        application {
            Window(onCloseRequest = {
                server.stop()
                exitApplication()
            }, title = "Graphviz DOT Viewer") {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                        dotFileContentViewer(dotFile)
                    }
                    Box(Modifier.weight(2f).fillMaxHeight().padding(16.dp)) {
                        jcefWebViewPanel(url = "http://localhost:$actualPort/")
                    }
                }
            }
        }
        thread(start = true) {
            val watcher = FileSystems.getDefault().newWatchService()
            val path = dotFile.parentFile.toPath()
            path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
            while (true) {
                val wk = watcher.take()
                wk.pollEvents().forEach { event ->
                    val changed = event.context() as? Path
                    if (changed != null && changed.endsWith(dotFile.name)) {
                        dotContentRef.set(dotFile.readText())
                        dotChangedChannel.trySend(Unit)
                    }
                }
                wk.reset()
            }
        }
    }
}

@Composable
fun dotFileContentViewer(dotFile: File) {
    var dotContent by remember { mutableStateOf(dotFile.readText()) }
    val scrollState = rememberScrollState()
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "DOT file content:",
                fontSize = 18.sp,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = dotContent, fontSize = 14.sp, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    DisposableEffect(dotFile) {
        val watcher = FileSystems.getDefault().newWatchService()
        val path = dotFile.parentFile.toPath()
        path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
        val watchThread = thread(start = true) {
            while (!Thread.currentThread().isInterrupted) {
                val wk = watcher.take()
                wk.pollEvents().forEach { event ->
                    val changed = event.context() as? Path
                    if (changed != null && changed.endsWith(dotFile.name)) {
                        dotContent = dotFile.readText()
                    }
                }
                wk.reset()
            }
        }
        onDispose {
            watchThread.interrupt()
            watcher.close()
        }
    }
}

@Composable
fun jcefWebViewPanel(url: String) {
    // Compose for Desktop: embed a SwingPanel with the JCEF browser
    ComposeSwingPanel(
        modifier = Modifier.fillMaxSize(), factory = {
            try {
                // JCEF setup
                val builder = CefAppBuilder()
                // Do not set installDir to null; use default temp dir or specify a directory if needed
                builder.setProgressHandler(ConsoleProgressHandler())
                val cefApp = builder.build()
                val client = cefApp.createClient()
                val browser: CefBrowser = client.createBrowser(url, false, false)
                browser.uiComponent
            } catch (ise: IllegalAccessError) {
                // This commonly happens on macOS with module access restrictions.
                System.err.println("JCEF initialization failed due to module access: ${'$'}ise")
                System.err.println("If you're on macOS, try running with JVM args:\n  --add-exports=java.desktop/sun.awt=ALL-UNNAMED\n  --add-opens=java.desktop/sun.awt=ALL-UNNAMED\n")
                // Try to open the system browser as a fallback
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                } catch (t: Throwable) {
                    System.err.println("Failed to open system browser: ${'$'}t")
                }
                // Return a simple Swing label instructing the user to open the URL
                javax.swing.JLabel("Unable to initialize embedded browser. Open: $url")
            }
        })
}

fun main(args: Array<String>) {
    DotViewerCli().main(args)
}
