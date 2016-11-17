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

package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import junit.framework.TestCase

/**
 * Test for {@link SimpleRelocator}.
 *
 * @author Benjamin Bentmann
 * @version $Id: SimpleRelocatorTest.java 1342979 2012-05-26 22:05:45Z bimargulies $
 *
 * Modified from org.apache.maven.plugins.shade.relocation.SimpleRelocatorTest.java
 * 
 * Modifications
 * @author John Engelman
 */
class SimpleRelocatorTest extends TestCase {

    ShadowStats stats

    @Override
    protected void setUp() {
      stats = new ShadowStats()
    }

    void testCanRelocatePath() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/Class")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/Class.class")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/bar/Class")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/bar/Class.class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("com/foo/bar/Class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("com/foo/bar/Class.class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/Foo/Class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/Foo/Class.class")))

        relocator = new SimpleRelocator("org.foo", null, null, Arrays.asList(
                [ "org.foo.Excluded", "org.foo.public.*", "org.foo.Public*Stuff" ] as String[]))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/Class")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/Class.class")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/excluded")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/Excluded")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/Excluded.class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/public")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/public/Class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/public/Class.class")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/publicRELOC/Class")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/PrivateStuff")))
        assertEquals(true, relocator.canRelocatePath(pathContext("org/foo/PrivateStuff.class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/PublicStuff")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/PublicStuff.class")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/PublicUtilStuff")))
        assertEquals(false, relocator.canRelocatePath(pathContext("org/foo/PublicUtilStuff.class")))
    }

    void testCanRelocateClass() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals(true, relocator.canRelocateClass(classContext("org.foo.Class")))
        assertEquals(true, relocator.canRelocateClass(classContext("org.foo.bar.Class")))
        assertEquals(false, relocator.canRelocateClass(classContext("com.foo.bar.Class")))
        assertEquals(false, relocator.canRelocateClass(classContext("org.Foo.Class")))

        relocator = new SimpleRelocator("org.foo", null, null, Arrays.asList(
                [ "org.foo.Excluded", "org.foo.public.*", "org.foo.Public*Stuff" ] as String[]))
        assertEquals(true, relocator.canRelocateClass(classContext("org.foo.Class")))
        assertEquals(true, relocator.canRelocateClass(classContext("org.foo.excluded")))
        assertEquals(false, relocator.canRelocateClass(classContext("org.foo.Excluded")))
        assertEquals(false, relocator.canRelocateClass(classContext("org.foo.public")))
        assertEquals(false, relocator.canRelocateClass(classContext("org.foo.public.Class")))
        assertEquals(true, relocator.canRelocateClass(classContext("org.foo.publicRELOC.Class")))
        assertEquals(true, relocator.canRelocateClass(classContext("org.foo.PrivateStuff")))
        assertEquals(false, relocator.canRelocateClass(classContext("org.foo.PublicStuff")))
        assertEquals(false, relocator.canRelocateClass(classContext("org.foo.PublicUtilStuff")))
    }

    void testCanRelocateRawString() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org/foo", null, null, null, true)
        assertEquals(true, relocator.canRelocatePath(pathContext("(I)org/foo/bar/Class")))

        relocator = new SimpleRelocator("^META-INF/org.foo.xml\$", null, null, null, true)
        assertEquals(true, relocator.canRelocatePath(pathContext("META-INF/org.foo.xml")))
    }

    //MSHADE-119, make sure that the easy part of this works.
    void testCanRelocateAbsClassPath() {
        SimpleRelocator relocator = new SimpleRelocator("org.apache.velocity", "org.apache.momentum", null, null)
        assertEquals("/org/apache/momentum/mass.properties", relocator.relocatePath(pathContext("/org/apache/velocity/mass.properties")))

    }

    void testRelocatePath() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals("hidden/org/foo/bar/Class.class", relocator.relocatePath(pathContext("org/foo/bar/Class.class")))

        relocator = new SimpleRelocator("org.foo", "private.stuff", null, null)
        assertEquals("private/stuff/bar/Class.class", relocator.relocatePath(pathContext("org/foo/bar/Class.class")))
    }

    void testRelocateClass() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals("hidden.org.foo.bar.Class", relocator.relocateClass(classContext("org.foo.bar.Class")))

        relocator = new SimpleRelocator("org.foo", "private.stuff", null, null)
        assertEquals("private.stuff.bar.Class", relocator.relocateClass(classContext("org.foo.bar.Class")))
    }

    void testRelocateRawString() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("Lorg/foo", "Lhidden/org/foo", null, null, true)
        assertEquals("(I)Lhidden/org/foo/bar/Class", relocator.relocatePath(pathContext("(I)Lorg/foo/bar/Class")))

        relocator = new SimpleRelocator("^META-INF/org.foo.xml\$", "META-INF/hidden.org.foo.xml", null, null, true)
        assertEquals("META-INF/hidden.org.foo.xml", relocator.relocatePath(pathContext("META-INF/org.foo.xml")))
    }
    
    protected RelocatePathContext pathContext(String path) {
        return RelocatePathContext.builder().path(path).stats(stats).build()
    }

    protected RelocateClassContext classContext(String className) {
        return RelocateClassContext.builder().className(className).stats(stats).build()
    }
}
