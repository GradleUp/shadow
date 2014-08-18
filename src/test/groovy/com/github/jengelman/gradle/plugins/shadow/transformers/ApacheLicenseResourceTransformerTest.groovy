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

import org.junit.Before
import org.junit.Test

import static org.junit.Assert.*

/**
 * Test for {@link ApacheLicenseResourceTransformer}.
 *
 * @author Benjamin Bentmann
 * @version $Id: ApacheLicenseResourceTransformerTest.java 673906 2008-07-04 05:03:20Z brett $
 *
 * Modified from org.apache.maven.plugins.shade.resources.ApacheLicenseResourceTransformerTest.java
 */
class ApacheLicenseResourceTransformerTest extends TransformerTestSupport {

    private ApacheLicenseResourceTransformer transformer

    static {
        /*
         * NOTE: The Turkish locale has an usual case transformation for the letters "I" and "i", making it a prime
         * choice to test for improper case-less string comparisions.
         */
        Locale.setDefault(new Locale("tr"))
    }

    @Before
    void setUp() {
        this.transformer = new ApacheLicenseResourceTransformer()
    }

    @Test
    void testCanTransformResource() {
        assertTrue(this.transformer.canTransformResource(getFileElement("META-INF/LICENSE")))
        assertTrue(this.transformer.canTransformResource(getFileElement("META-INF/LICENSE.TXT")))
        assertTrue(this.transformer.canTransformResource(getFileElement("META-INF/License.txt")))
        assertFalse(this.transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF")))
    }

}
