import org.apache.logging.log4j.core.config.plugins.processor.PluginEntry
import org.apache.maven.plugins.shade.relocation.Relocator
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.Collections
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.math.max

/**
 * 'log4j-maven-shade-plugin' transformer implementation.
 */
public class Log4j2PluginCacheFileTransformer public constructor() : ReproducibleResourceTransformer {
    /**
     * Log4j config files to share across the transformation stages.
     */
    private val tempFiles: MutableList<Path>

    /**
     * [Relocator] instances to share across the transformation stages.
     */
    private val tempRelocators: MutableList<Relocator>

    /**
     * Store youngest (i.e. largest millisecond) so that we can produce reproducible jar file
     */
    private var youngestTime: Long = 0

    /**
     * Default constructor, initializing internal state.
     */
    init {
        tempRelocators = ArrayList<Relocator>()
        tempFiles = ArrayList<Path>()
    }

    /**
     * @param resource resource to check
     * @return true when resource is recognized as log4j-plugin-cache file
     */
    public override fun canTransformResource(resource: String?): Boolean {
        return PluginProcessor.PLUGIN_CACHE_FILE == resource
    }

    @Deprecated("")
    public override fun processResource(resource: String?, `is`: InputStream?, relocators: MutableList<Relocator?>?) {
        // stub
    }

    /**
     * @param resource      ignored parameter
     * @param resourceInput resource input stream to save in temp file
     * for next stage
     * @param relocators    relocators to keep for next stage
     * @throws IOException thrown by file writing errors
     */
    @Throws(IOException::class)
    public override fun processResource(
        resource: String?, resourceInput: InputStream, relocators: MutableList<Relocator?>?, time: Long
    ) {
        val tempFile = Files.createTempFile("Log4j2Plugins", "dat")
        Files.copy(resourceInput, tempFile, StandardCopyOption.REPLACE_EXISTING)
        tempFiles.add(tempFile)
        youngestTime = max(youngestTime.toDouble(), time.toDouble()).toLong()

        if (relocators != null) {
            this.tempRelocators.addAll(relocators)
        }
    }

    /**
     * @return true if any dat file collected
     */
    public override fun hasTransformedResource(): Boolean {
        return tempFiles.size > 0
    }

    /**
     * Stores all previously collected log4j-cache-files to the target jar.
     *
     * @param jos jar output
     * @throws IOException When the IO blows up
     */
    @Throws(IOException::class)
    public override fun modifyOutputStream(jos: JarOutputStream) {
        try {
            val aggregator: PluginCache = PluginCache()
            aggregator.loadCacheFiles(this.urls)
            relocatePlugin(tempRelocators, aggregator.getAllCategories())
            putJarEntry(jos)
            // prevent the aggregator to close the jar output
            val outputStream: CloseShieldOutputStream = CloseShieldOutputStream(jos)
            aggregator.writeCache(outputStream)
        } finally {
            deleteTempFiles()
        }
    }

    @get:Throws(MalformedURLException::class)
    private val urls: Enumeration<URL?>
        get() {
            val urls: MutableList<URL?> = ArrayList<URL?>()
            for (tempFile in tempFiles) {
                val url = tempFile.toUri().toURL()
                urls.add(url)
            }
            return Collections.enumeration<URL?>(urls)
        }

    /**
     * Applies the given `relocators` to the `aggregator`.
     *
     * @param relocators           relocators.
     * @param aggregatorCategories all categories of the aggregator
     */
    /* default */
    public fun relocatePlugin(
        relocators: MutableList<Relocator>,
        aggregatorCategories: MutableMap<String?, MutableMap<String?, PluginEntry?>?>
    ) {
        for (categoryEntry in aggregatorCategories.entries) {
            for (pluginMapEntry in categoryEntry.value!!.entries) {
                val pluginEntry: PluginEntry = pluginMapEntry.value!!
                val originalClassName = pluginEntry.getClassName()

                val matchingRelocator: Relocator? = findFirstMatchingRelocator(originalClassName, relocators)

                if (matchingRelocator != null) {
                    val newClassName: String? = matchingRelocator.relocateClass(originalClassName)
                    pluginEntry.setClassName(newClassName)
                }
            }
        }
    }

    private fun findFirstMatchingRelocator(originalClassName: String?, relocators: MutableList<Relocator>): Relocator? {
        var result: Relocator? = null
        for (relocator in relocators) {
            if (relocator.canRelocateClass(originalClassName)) {
                result = relocator
                break
            }
        }
        return result
    }

    @Throws(IOException::class)
    private fun putJarEntry(jos: JarOutputStream) {
        val jarEntry: JarEntry = JarEntry(PluginProcessor.PLUGIN_CACHE_FILE)

        // Set time to youngest timestamp, to ensure reproducible output.
        val fileTime = FileTime.fromMillis(youngestTime)
        jarEntry.setLastModifiedTime(fileTime)

        jos.putNextEntry(jarEntry)
    }

    @Throws(IOException::class)
    private fun deleteTempFiles() {
        val pathIterator = tempFiles.listIterator()
        while (pathIterator.hasNext()) {
            val path = pathIterator.next()
            Files.deleteIfExists(path)
            pathIterator.remove()
        }
    }
}
