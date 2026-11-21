package com.fraudshield.app

/**
 * Remembers which files were already seen so the first run does not flag the whole existing library (5.5).
 * Storage is injected (a file in the app, a list in tests). Ids are short hashes of "path|lastModified", so a
 * modified file gets a new id and is reported again.
 *
 * Cap: when more than [cap] ids exist the OLDEST are dropped. The cap is large (50,000) so a normal phone library
 * never loses its baseline; old files only reappear after 50,000 newer files have been seen.
 */
class BaselineStore(
    private val load: () -> List<String>?,
    private val save: (List<String>) -> Unit,
    private val cap: Int = 50_000
) {
    private var seen: LinkedHashSet<String>? = null

    /**
     * Returns the ids that were NOT seen before and remembers them.
     * The very first call (nothing stored yet) only records the baseline and returns an empty list; the baseline is
     * saved even when [ids] is empty, so an empty folder on first run still counts as "baseline taken".
     */
    @Synchronized
    fun newOnly(ids: List<String>): List<String> {
        val stored = seen ?: load()?.let { LinkedHashSet(it) }
        val firstRun = stored == null
        val set = stored ?: LinkedHashSet()
        val fresh = ids.filter { it !in set }
        set.addAll(ids)
        var changed = firstRun || fresh.isNotEmpty()
        if (set.size > cap) {
            val it = set.iterator()
            var drop = set.size - cap
            while (drop > 0 && it.hasNext()) { it.next(); it.remove(); drop-- }
            changed = true
        }
        seen = set
        if (changed) save(set.toList())
        return if (firstRun) emptyList() else fresh
    }

    companion object {
        /** 64-bit FNV-1a of "path|mtime" as 16 hex chars. */
        fun idOf(path: String, lastModified: Long): String {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (c in "$path|$lastModified") { h = h xor c.code.toLong(); h *= 0x100000001b3L }
            return "%016x".format(h)
        }
    }
}
