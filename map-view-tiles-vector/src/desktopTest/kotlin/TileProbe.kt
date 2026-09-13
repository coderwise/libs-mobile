
import com.coderwise.libs.mapview.tiles.vector.buildRenderTile
import com.coderwise.libs.mapview.tiles.vector.VectorStyle
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import java.io.File
import kotlin.test.Test

class TileProbe {
    @Test
    fun probe() {
        val dir = File("/private/tmp/claude-501/-Users-andry-Development-coderwise-maps-mobile/8edcac43-298a-44b6-92a8-cd3d03ae98f5/scratchpad")
        val style = VectorStyle()
        listOf("14_4824_6159.pbf", "14_4824_6158.pbf", "14_4823_6159.pbf").forEach { n ->
            val bytes = File(dir, n).readBytes()
            val layers = decodeMvt(bytes)
            val r = runCatching {
                val t0 = System.nanoTime()
                val rt = buildRenderTile(layers, style)
                rt to (System.nanoTime() - t0) / 1e6
            }
            r.fold(
                { (rt, ms) -> println("PROBE $n ok build=${"%.1f".format(ms)}ms ops=${rt.javaClass.getDeclaredField("ops").apply { isAccessible = true }.get(rt).let { (it as List<*>).size }}") },
                { e -> println("PROBE $n THREW ${e::class.qualifiedName}: ${e.message}") }
            )
        }
    }
}
