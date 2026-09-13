import com.coderwise.libs.mapview.tiles.vector.decodeVectorTile
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class TileProbe {
    @Test
    fun probe() = runBlocking {
        val dir = File("/private/tmp/claude-501/-Users-andry-Development-coderwise-maps-mobile/8edcac43-298a-44b6-92a8-cd3d03ae98f5/scratchpad")
        listOf("14_4824_6159.pbf", "14_4824_6158.pbf", "14_4823_6159.pbf").forEach { n ->
            val f = File(dir, n)
            if (!f.exists()) { println("PROBE missing $n"); return@forEach }
            val bytes = f.readBytes()
            val t0 = System.nanoTime()
            val tile = decodeVectorTile(14, bytes)
            val ms = (System.nanoTime() - t0) / 1e6
            val layers = runCatching { decodeMvt(bytes) }.getOrNull()
            println("PROBE $n bytes=${bytes.size} decode=${"%.1f".format(ms)}ms tile=${tile != null} labels=${tile?.labels?.size} water=${tile?.water != null} mvtLayers=${layers?.size}")
        }
    }
}
