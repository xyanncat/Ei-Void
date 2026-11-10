package com.voidapp.core.git

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises git operations per repository.
 *
 * Two callers (the UI and the agent) touching one `.git` directory make JGit
 * fail with `index.lock` contention, so every write operation takes the lock
 * of its workspace first.
 */
class RepoLocks {

    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val lock = locks.getOrPut(key) { Mutex() }
        return lock.withLock { block() }
    }
}
