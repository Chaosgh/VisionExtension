
package de.chaos

import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton


@Singleton
object Initializer : Initializable {

    override suspend fun initialize() {
    }

    override suspend fun shutdown() {
    }
}
