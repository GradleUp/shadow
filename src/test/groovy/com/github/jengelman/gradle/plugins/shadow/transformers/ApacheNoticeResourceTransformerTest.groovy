/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.fail

/**
 * Tests {@link ApacheLicenseResourceTransformer} parameters.
 *
 * Modified from org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformerParameterTests.java
 */
class ApacheNoticeResourceTransformerTest extends TransformerTestSupport<ApacheNoticeResourceTransformer> {

    private static final String NOTICE_RESOURCE = "META-INF/NOTICE"
    private static ShadowStats stats

    static {
        /*
         * NOTE: The Turkish locale has an usual case transformation for the letters "I" and "i", making it a prime
         * choice to test for improper case-less string comparisions.
         */
        Locale.setDefault(new Locale("tr"))
    }

    @BeforeEach
    void setUp() {
        transformer = new ApacheNoticeResourceTransformer(objectFactory)
        stats = new ShadowStats()
    }

    @Test
    void testCanTransformResource() {
        assertTrue(transformer.canTransformResource(getFileElement("META-INF/NOTICE")))
        assertTrue(transformer.canTransformResource(getFileElement("META-INF/NOTICE.TXT")))
        assertTrue(transformer.canTransformResource(getFileElement("META-INF/Notice.txt")))
        assertTrue(transformer.canTransformResource(getFileElement("META-INF/NOTICE.md")))
        assertTrue(transformer.canTransformResource(getFileElement("META-INF/Notice.md")))
    }

    @Test
    void testNoParametersShouldNotThrowNullPointerWhenNoInput() {
        processAndFailOnNullPointer("")
    }

    @Test
    void testNoParametersShouldNotThrowNullPointerWhenNoLinesOfInput() {
        processAndFailOnNullPointer("Some notice text")
    }

    @Test
    void testNoParametersShouldNotThrowNullPointerWhenOneLineOfInput() {
        processAndFailOnNullPointer("Some notice text\n")
    }

    @Test
    void testNoParametersShouldNotThrowNullPointerWhenTwoLinesOfInput() {
        processAndFailOnNullPointer("Some notice text\nSome notice text\n")
    }

    @Test
    void testNoParametersShouldNotThrowNullPointerWhenLineStartsWithSlashSlash() {
        processAndFailOnNullPointer("Some notice text\n//Some notice text\n")
    }

    @Test
    void testNoParametersShouldNotThrowNullPointerWhenLineIsSlashSlash() {
        processAndFailOnNullPointer("//\n")
    }

    @Test
    void testNoParametersShouldNotThrowNullPointerWhenLineIsEmpty() {
        processAndFailOnNullPointer("\n")
    }

    private static void processAndFailOnNullPointer(final String noticeText) {
        try {
            final ByteArrayInputStream noticeInputStream = new ByteArrayInputStream(noticeText.getBytes())
            final List<Relocator> emptyList = Collections.emptyList()
            transformer.transform(TransformerContext.builder().path(NOTICE_RESOURCE).inputStream(noticeInputStream).relocators(emptyList).stats(stats).build())
        }
        catch (NullPointerException ignored) {
            fail("Null pointer should not be thrown when no parameters are set.")
        }
    }
}
