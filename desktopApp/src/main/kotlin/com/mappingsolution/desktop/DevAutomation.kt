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
 * - `GET /info` → window size in AWT units, the display scale and the side panel's screen
 * - `GET /click?x=&y=` → left click at content-pane coordinates (AWT units)
 * - `GET /clickFeature?kind=poi|route&name=` → left click on a named POI or route on the map
 * - `GET /type?text=` → types text into the focused Compose text field
 * - `GET /camera?lat=&lng=&zoom=` → moves the map camera
 * - `GET /doubleClick?x=&y=` → left double click at content-pane coordinates
 * - `GET /locate` → same as double-clicking the map: flies to the computer's location
 * - `GET /drag?x1=&y1=&x2=&y2=&button=1|3` → drags between two points with a mouse button
 * - `GET /importMbtiles?path=` → imports an MBTiles file without the file dialog
 */
internal object DevAutomation {
    private const val TAG = "DevAutomation"

    /** Set by the map screen: projects a named feature to map-relative logical pixels. */
    @Volatile
    var featureLocator: ((kind: String, name: String) -> DpOffset?)? = null

    /** The screen shown in the side panel, or `closed`. */
    @Volatile
    var panelState: String = "closed"

    /** Set by the map screen: flies to the computer's location. */
    @Volatile
    var locator: (() -> Unit)? = null

    /** Set by the map screen: animates the camera to a position. */
    @Volatile
    var cameraMover: ((lat: Double, lng: Double, zoom: Double) -> Unit)? = null

    /** Set by the app: starts an MBTiles import from a local path. */
    @Volatile
    var mbtilesImporter: ((path: String) -> Unit)? = null

    fun startIfEnabled(window: Window) {
        val port = System.getenv("MS_AUTOMATION_PORT")?.toIntOrNull() ?: return
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
        server.createContext("/info") { exchange ->
            val root = rootComponent(window)
            val scale = window.graphicsConfiguration.defaultTransform.scaleX
            respond(exchange, "width=${root.width} height=${root.height} scale=$scale panel=$panelState")
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
        server.createContext("/doubleClick") { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery)
            val x = query["x"]?.toIntOrNull() ?: 0
            val y = query["y"]?.toIntOrNull() ?: 0
            SwingUtilities.invokeAndWait { click(rootComponent(window), x, y, clickCount = 1) }
            Thread.sleep(120)
            SwingUtilities.invokeAndWait { click(rootComponent(window), x, y, clickCount = 2) }
            Thread.sleep(300)
            respond(exchange, "double-clicked $x,$y")
        }
        server.createContext("/locate") { exchange ->
            SwingUtilities.invokeAndWait { locator?.invoke() }
            respond(exchange, "locating")
        }
        server.createContext("/drag") { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery)
            val (x1, y1, x2, y2) = listOf("x1", "y1", "x2", "y2").map { query[it]?.toIntOrNull() ?: 0 }
            val right = query["button"] == "3"
            val root = rootComponent(window)
            val queue = Toolkit.getDefaultToolkit().systemEventQueue
            val button = if (right) MouseEvent.BUTTON3 else MouseEvent.BUTTON1
            val mask = if (right) MouseEvent.BUTTON3_DOWN_MASK else MouseEvent.BUTTON1_DOWN_MASK
            var target: Component = root
            SwingUtilities.invokeAndWait { target = SwingUtilities.getDeepestComponentAt(root, x1, y1) ?: root }
            fun post(id: Int, x: Int, y: Int, modifiers: Int, clicks: Int, btn: Int) {
                val p = SwingUtilities.convertPoint(root, x, y, target)
                queue.postEvent(MouseEvent(target, id, System.currentTimeMillis(), modifiers, p.x, p.y, clicks, false, btn))
            }
            post(MouseEvent.MOUSE_MOVED, x1, y1, 0, 0, MouseEvent.NOBUTTON)
            post(MouseEvent.MOUSE_PRESSED, x1, y1, mask, 1, button)
            for (step in 1..20) {
                Thread.sleep(15)
                post(MouseEvent.MOUSE_DRAGGED, x1 + (x2 - x1) * step / 20, y1 + (y2 - y1) * step / 20, mask, 0, MouseEvent.NOBUTTON)
            }
            post(MouseEvent.MOUSE_RELEASED, x2, y2, 0, 1, button)
            Thread.sleep(300)
            respond(exchange, "dragged $x1,$y1 -> $x2,$y2")
        }
        server.createContext("/importMbtiles") { exchange ->
            val path = parseQuery(exchange.requestURI.rawQuery)["path"].orEmpty()
            mbtilesImporter?.invoke(path)
            respond(exchange, "importing $path")
        }
        server.start()
        AppLog.i(TAG, "Automation listening on http://localhost:$port")
    }

    private fun rootComponent(window: Window): Component =
        (window as? javax.swing.RootPaneContainer)?.contentPane ?: window

    private fun click(root: Component, x: Int, y: Int, clickCount: Int = 1): String {
        val target = SwingUtilities.getDeepestComponentAt(root, x, y) ?: root
        val point = SwingUtilities.convertPoint(root, x, y, target)
        val queue = Toolkit.getDefaultToolkit().systemEventQueue
        val now = System.currentTimeMillis()
        fun post(id: Int, time: Long, modifiers: Int, clickCount: Int, button: Int) = queue.postEvent(
            MouseEvent(target, id, time, modifiers, point.x, point.y, clickCount, false, button)
        )
        post(MouseEvent.MOUSE_ENTERED, now, 0, 0, MouseEvent.NOBUTTON)
        post(MouseEvent.MOUSE_MOVED, now, 0, 0, MouseEvent.NOBUTTON)
        post(MouseEvent.MOUSE_PRESSED, now + 10, MouseEvent.BUTTON1_DOWN_MASK, clickCount, MouseEvent.BUTTON1)
        post(MouseEvent.MOUSE_RELEASED, now + 60, 0, clickCount, MouseEvent.BUTTON1)
        post(MouseEvent.MOUSE_CLICKED, now + 60, 0, clickCount, MouseEvent.BUTTON1)
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
