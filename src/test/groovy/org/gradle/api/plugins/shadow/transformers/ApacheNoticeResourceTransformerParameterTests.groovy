package org.gradle.api.plugins.shadow.transformers

import junit.framework.TestCase
import org.gradle.api.plugins.shadow.relocation.Relocator


/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Tests {@link ApacheLicenseResourceTransformer} parameters.
 *
 * Modified from org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformerParameterTests.java
 */
public class ApacheNoticeResourceTransformerParameterTests extends TestCase {

    private static final String NOTICE_RESOURCE = "META-INF/NOTICE"
    private ApacheNoticeResourceTransformer subject

    protected void setUp() {
        super.setUp()
        subject = new ApacheNoticeResourceTransformer()
    }

    public void testNoParametersShouldNotThrowNullPointerWhenNoInput() {
        processAndFailOnNullPointer("")
    }

    public void testNoParametersShouldNotThrowNullPointerWhenNoLinesOfInput() {
        processAndFailOnNullPointer("Some notice text")
    }

    public void testNoParametersShouldNotThrowNullPointerWhenOneLineOfInput() {
        processAndFailOnNullPointer("Some notice text\n")
    }

    public void testNoParametersShouldNotThrowNullPointerWhenTwoLinesOfInput() {
        processAndFailOnNullPointer("Some notice text\nSome notice text\n")
    }

    public void testNoParametersShouldNotThrowNullPointerWhenLineStartsWithSlashSlash() {
        processAndFailOnNullPointer("Some notice text\n//Some notice text\n")
    }

    public void testNoParametersShouldNotThrowNullPointerWhenLineIsSlashSlash() {
        processAndFailOnNullPointer("//\n")
    }

    public void testNoParametersShouldNotThrowNullPointerWhenLineIsEmpty() {
        processAndFailOnNullPointer("\n")
    }

    private void processAndFailOnNullPointer(final String noticeText) {
        try {
            final ByteArrayInputStream noticeInputStream = new ByteArrayInputStream(noticeText.getBytes())
            final List<Relocator> emptyList = Collections.emptyList()
            subject.transform(NOTICE_RESOURCE, noticeInputStream, emptyList)
        }
        catch (NullPointerException e) {
            fail("Null pointer should not be thrown when no parameters are set.")
        }
    }
}
