package com.mappingsolution.desktop

import androidx.compose.ui.unit.DpOffset
import com.mappingsolution.data.util.AppLog
import com.sun.net.httpserver.HttpServer
import java.awt.Component
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.swing.SwingUtilities

/**
 * Development-only remote control, enabled by setting `MS_AUTOMATION_PORT`.
 *
 * Input is dispatched as AWT events inside this process, so automated tests never move the
 * real mouse cursor or steal focus from whatever the user is doing on the same machine.
 * Endpoints (localhost only):
 * - `GET /info` → window size in AWT units and the display scale
 * - `GET /click?x=&y=` → left click at content-pane coordinates (AWT units)
 * - `GET /clickFeature?kind=poi|route&name=` → left click on a named POI or route on the map
 * - `GET /type?text=` → types text into the focused Compose text field
 * - `GET /camera?lat=&lng=&zoom=` → moves the map camera
 */
internal object DevAutomation {
    private const val TAG = "DevAutomation"

    /** Set by the map screen: projects a named feature to map-relative logical pixels. */
    @Volatile
    var featureLocator: ((kind: String, name: String) -> DpOffset?)? = null

    /** Set by the map screen: animates the camera to a position. */
    @Volatile
    var cameraMover: ((lat: Double, lng: Double, zoom: Double) -> Unit)? = null

    fun startIfEnabled(window: Window) {
        val port = System.getenv("MS_AUTOMATION_PORT")?.toIntOrNull() ?: return
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
        server.createContext("/info") { exchange ->
            val root = rootComponent(window)
            val scale = window.graphicsConfiguration.defaultTransform.scaleX
            respond(exchange, "width=${root.width} height=${root.height} scale=$scale")
        }
        server.createContext("/tree") { exchange ->
            val out = StringBuilder()
            fun walk(c: Component, depth: Int) {
                out.append("  ".repeat(depth)).append(c.javaClass.name)
                    .append(" bounds=").append(c.bounds).append(" visible=").append(c.isVisible)
                    .append(" mouseListeners=").append(c.mouseListeners.size).appendLine()
                (c as? java.awt.Container)?.components?.forEach { walk(it, depth + 1) }
            }
            SwingUtilities.invokeAndWait { walk(window, 0) }
            respond(exchange, out.toString())
        }
        server.createContext("/click") { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery)
            val x = query["x"]?.toIntOrNull()
            val y = query["y"]?.toIntOrNull()
            if (x == null || y == null) {
                respond(exchange, "usage: /click?x=<int>&y=<int>", status = 400)
            } else {
                var target = ""
                SwingUtilities.invokeAndWait { target = click(rootComponent(window), x, y) }
                Thread.sleep(300)
                respond(exchange, "clicked $x,$y on $target")
            }
        }
        server.createContext("/clickFeature") { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery)
            var location: DpOffset? = null
            SwingUtilities.invokeAndWait {
                location = featureLocator?.invoke(query["kind"].orEmpty(), query["name"].orEmpty())
            }
            val point = location
            if (point == null) {
                respond(exchange, "feature not found or not on screen", status = 404)
            } else {
                val x = point.x.value.toInt()
                val y = point.y.value.toInt()
                SwingUtilities.invokeAndWait { click(rootComponent(window), x, y) }
                Thread.sleep(300)
                respond(exchange, "clicked ${query["kind"]} '${query["name"]}' at $x,$y")
            }
        }
        server.createContext("/type") { exchange ->
            val text = parseQuery(exchange.requestURI.rawQuery)["text"].orEmpty()
            SwingUtilities.invokeAndWait {
                val target = SwingUtilities.getDeepestComponentAt(rootComponent(window), 1, 1) ?: rootComponent(window)
                val queue = Toolkit.getDefaultToolkit().systemEventQueue
                text.forEach { char ->
                    queue.postEvent(
                        KeyEvent(target, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, char)
                    )
                }
            }
            Thread.sleep(300)
            respond(exchange, "typed ${text.length} chars")
        }
        server.createContext("/camera") { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery)
            val lat = query["lat"]?.toDoubleOrNull()
            val lng = query["lng"]?.toDoubleOrNull()
            val zoom = query["zoom"]?.toDoubleOrNull()
            if (lat == null || lng == null || zoom == null) {
                respond(exchange, "usage: /camera?lat=&lng=&zoom=", status = 400)
            } else {
                SwingUtilities.invokeAndWait { cameraMover?.invoke(lat, lng, zoom) }
                respond(exchange, "camera moving to $lat,$lng z$zoom")
            }
        }
        server.start()
        AppLog.i(TAG, "Automation listening on http://localhost:$port")
    }

    private fun rootComponent(window: Window): Component =
        (window as? javax.swing.RootPaneContainer)?.contentPane ?: window

    private fun click(root: Component, x: Int, y: Int): String {
        val target = SwingUtilities.getDeepestComponentAt(root, x, y) ?: root
        val point = SwingUtilities.convertPoint(root, x, y, target)
        val queue = Toolkit.getDefaultToolkit().systemEventQueue
        val now = System.currentTimeMillis()
        fun post(id: Int, time: Long, modifiers: Int, clickCount: Int, button: Int) = queue.postEvent(
            MouseEvent(target, id, time, modifiers, point.x, point.y, clickCount, false, button)
        )
        post(MouseEvent.MOUSE_ENTERED, now, 0, 0, MouseEvent.NOBUTTON)
        post(MouseEvent.MOUSE_MOVED, now, 0, 0, MouseEvent.NOBUTTON)
        post(MouseEvent.MOUSE_PRESSED, now + 10, MouseEvent.BUTTON1_DOWN_MASK, 1, MouseEvent.BUTTON1)
        post(MouseEvent.MOUSE_RELEASED, now + 60, 0, 1, MouseEvent.BUTTON1)
        post(MouseEvent.MOUSE_CLICKED, now + 60, 0, 1, MouseEvent.BUTTON1)
        return target.javaClass.name
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { '=' in it }.associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String, status: Int = 200) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
