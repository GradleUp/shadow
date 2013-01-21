package org.gradle.api.plugins.shadow.filter

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


import junit.framework.TestCase

/**
 * @author Benjamin Bentmann
 *
 * Modified from org.apache.maven.plugins.shade.filter.SimpleFilterTest.java
 */
public class SimpleFilterTest extends TestCase {

    public void testIsFiltered() {
        SimpleFilter filter

        filter = new SimpleFilter(null, null, null)
        assertFalse(filter.isFiltered("a.properties"))
        assertFalse(filter.isFiltered("org/Test.class"))

        filter = new SimpleFilter(null, Collections.<String> emptyList(), Collections.<String> emptyList())
        assertFalse(filter.isFiltered("a.properties"))
        assertFalse(filter.isFiltered("org/Test.class"))

        filter = new SimpleFilter(null, Collections.singletonList("org/Test.class"), Collections.<String> emptyList())
        assertTrue(filter.isFiltered("a.properties"))
        assertFalse(filter.isFiltered("org/Test.class"))
        assertTrue(filter.isFiltered("org/Test.properties"))

        filter = new SimpleFilter(null, Collections.<String> emptyList(), Collections.singletonList("org/Test.class"))
        assertFalse(filter.isFiltered("a.properties"))
        assertTrue(filter.isFiltered("org/Test.class"))
        assertFalse(filter.isFiltered("org/Test.properties"))

        filter = new SimpleFilter(null, Collections.singletonList("**/a.properties"), Collections.<String> emptyList())
        assertFalse(filter.isFiltered("a.properties"))
        assertFalse(filter.isFiltered("org/a.properties"))
        assertFalse(filter.isFiltered("org/maven/a.properties"))
        assertTrue(filter.isFiltered("org/maven/a.class"))

        filter = new SimpleFilter(null, Collections.<String> emptyList(), Collections.singletonList("org/*"))
        assertFalse(filter.isFiltered("Test.class"))
        assertTrue(filter.isFiltered("org/Test.class"))
        assertFalse(filter.isFiltered("org/apache/Test.class"))

        filter = new SimpleFilter(null, Collections.<String> emptyList(), Collections.singletonList("org/**"))
        assertFalse(filter.isFiltered("Test.class"))
        assertTrue(filter.isFiltered("org/Test.class"))
        assertTrue(filter.isFiltered("org/apache/Test.class"))

        filter = new SimpleFilter(null, Collections.<String> emptyList(), Collections.singletonList("org/"))
        assertFalse(filter.isFiltered("Test.class"))
        assertTrue(filter.isFiltered("org/Test.class"))
        assertTrue(filter.isFiltered("org/apache/Test.class"))
    }

}
