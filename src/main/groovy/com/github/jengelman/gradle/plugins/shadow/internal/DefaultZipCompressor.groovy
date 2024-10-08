/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jengelman.gradle.plugins.shadow.internal

import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.UncheckedIOException

class DefaultZipCompressor implements ZipCompressor {
    private final int entryCompressionMethod
    private final Zip64Mode zip64Mode

     DefaultZipCompressor(boolean allowZip64Mode, int entryCompressionMethod) {
        this.entryCompressionMethod = entryCompressionMethod
        zip64Mode = allowZip64Mode ? Zip64Mode.AsNeeded : Zip64Mode.Never
    }

    @Override
    ZipOutputStream createArchiveOutputStream(File destination) {
        try {
            ZipOutputStream zipOutputStream = new ZipOutputStream(destination)
            zipOutputStream.setUseZip64(zip64Mode)
            zipOutputStream.setMethod(entryCompressionMethod)
            return zipOutputStream
        } catch (Exception e) {
            String message = String.format("Unable to create ZIP output stream for file %s.", destination)
            throw new UncheckedIOException(message, e)
        }
    }

}
