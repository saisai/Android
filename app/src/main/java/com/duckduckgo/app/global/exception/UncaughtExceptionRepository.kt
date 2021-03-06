/*
 * Copyright (c) 2019 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.global.exception

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


interface UncaughtExceptionRepository {
    suspend fun recordUncaughtException(e: Throwable?, exceptionSource: UncaughtExceptionSource)
    suspend fun getExceptions(): List<UncaughtExceptionEntity>
    suspend fun deleteException(id: Long)
}

class UncaughtExceptionRepositoryDb(
    private val uncaughtExceptionDao: UncaughtExceptionDao,
    private val rootExceptionFinder: RootExceptionFinder
) : UncaughtExceptionRepository {

    private var lastSeenException: Throwable? = null

    override suspend fun recordUncaughtException(e: Throwable?, exceptionSource: UncaughtExceptionSource) {
        return withContext(Dispatchers.IO) {
            if (e == lastSeenException) {
                return@withContext
            }

            Timber.e(e, "Uncaught exception - $exceptionSource")

            val rootCause = rootExceptionFinder.findRootException(e)
            val exceptionEntity = UncaughtExceptionEntity(message = extractExceptionCause(rootCause), exceptionSource = exceptionSource)
            uncaughtExceptionDao.add(exceptionEntity)

            lastSeenException = e
        }
    }

    override suspend fun getExceptions(): List<UncaughtExceptionEntity> {
        return withContext(Dispatchers.IO) {
            uncaughtExceptionDao.all()
        }
    }

    private fun extractExceptionCause(e: Throwable?): String {
        if (e == null) {
            return "Exception missing"
        }

        return "${e.javaClass.name} - ${e.stackTrace?.firstOrNull()}"
    }

    override suspend fun deleteException(id: Long) {
        return withContext(Dispatchers.IO) {
            uncaughtExceptionDao.delete(id)
        }
    }
}