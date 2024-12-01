/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
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
package com.github.jengelman.gradle.plugins.shadow.relocation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link SimpleRelocator}.
 *
 * @author Benjamin Bentmann
 *
 */
public class SimpleRelocatorTestNew {

    @Test
    public void testNoNpeRelocateClass() {
        new SimpleRelocator("foo", "bar", null, null, true).relocateClass("foo");
    }

    @Test
    public void testCanRelocatePath() {
        SimpleRelocator relocator;

        relocator = new SimpleRelocator("org.foo", null, null, null);
        assertTrue(relocator.canRelocatePath("org/foo/Class"));
        assertTrue(relocator.canRelocatePath("org/foo/Class.class"));
        assertTrue(relocator.canRelocatePath("org/foo/bar/Class"));
        assertTrue(relocator.canRelocatePath("org/foo/bar/Class.class"));
        assertFalse(relocator.canRelocatePath("com/foo/bar/Class"));
        assertFalse(relocator.canRelocatePath("com/foo/bar/Class.class"));
        assertFalse(relocator.canRelocatePath("org/Foo/Class"));
        assertFalse(relocator.canRelocatePath("org/Foo/Class.class"));

        relocator = new SimpleRelocator(
                "org.foo", null, null, Arrays.asList("org.foo.Excluded", "org.foo.public.*", "org.foo.Public*Stuff"));
        assertTrue(relocator.canRelocatePath("org/foo/Class"));
        assertTrue(relocator.canRelocatePath("org/foo/Class.class"));
        assertTrue(relocator.canRelocatePath("org/foo/excluded"));
        assertFalse(relocator.canRelocatePath("org/foo/Excluded"));
        assertFalse(relocator.canRelocatePath("org/foo/Excluded.class"));
        assertFalse(relocator.canRelocatePath("org/foo/public"));
        assertFalse(relocator.canRelocatePath("org/foo/public/Class"));
        assertFalse(relocator.canRelocatePath("org/foo/public/Class.class"));
        assertTrue(relocator.canRelocatePath("org/foo/publicRELOC/Class"));
        assertTrue(relocator.canRelocatePath("org/foo/PrivateStuff"));
        assertTrue(relocator.canRelocatePath("org/foo/PrivateStuff.class"));
        assertFalse(relocator.canRelocatePath("org/foo/PublicStuff"));
        assertFalse(relocator.canRelocatePath("org/foo/PublicStuff.class"));
        assertFalse(relocator.canRelocatePath("org/foo/PublicUtilStuff"));
        assertFalse(relocator.canRelocatePath("org/foo/PublicUtilStuff.class"));
    }

    @Test
    public void testCanRelocateClass() {
        SimpleRelocator relocator;

        relocator = new SimpleRelocator("org.foo", null, null, null);
        assertTrue(relocator.canRelocateClass("org.foo.Class"));
        assertTrue(relocator.canRelocateClass("org.foo.bar.Class"));
        assertFalse(relocator.canRelocateClass("com.foo.bar.Class"));
        assertFalse(relocator.canRelocateClass("org.Foo.Class"));

        relocator = new SimpleRelocator(
                "org.foo", null, null, Arrays.asList("org.foo.Excluded", "org.foo.public.*", "org.foo.Public*Stuff"));
        assertTrue(relocator.canRelocateClass("org.foo.Class"));
        assertTrue(relocator.canRelocateClass("org.foo.excluded"));
        assertFalse(relocator.canRelocateClass("org.foo.Excluded"));
        assertFalse(relocator.canRelocateClass("org.foo.public"));
        assertFalse(relocator.canRelocateClass("org.foo.public.Class"));
        assertTrue(relocator.canRelocateClass("org.foo.publicRELOC.Class"));
        assertTrue(relocator.canRelocateClass("org.foo.PrivateStuff"));
        assertFalse(relocator.canRelocateClass("org.foo.PublicStuff"));
        assertFalse(relocator.canRelocateClass("org.foo.PublicUtilStuff"));
    }

    @Test
    public void testCanRelocateRawString() {
        SimpleRelocator relocator;

        relocator = new SimpleRelocator("org/foo", null, null, null, true);
        assertTrue(relocator.canRelocatePath("(I)org/foo/bar/Class;"));

        relocator = new SimpleRelocator("^META-INF/org.foo.xml$", null, null, null, true);
        assertTrue(relocator.canRelocatePath("META-INF/org.foo.xml"));
    }

    // MSHADE-119, make sure that the easy part of this works.
    @Test
    public void testCanRelocateAbsClassPath() {
        SimpleRelocator relocator = new SimpleRelocator("org.apache.velocity", "org.apache.momentum", null, null);
        assertEquals(
                "/org/apache/momentum/mass.properties", relocator.relocatePath("/org/apache/velocity/mass.properties"));
    }

    @Test
    public void testCanRelocateAbsClassPathWithExcludes() {
        SimpleRelocator relocator = new SimpleRelocator(
                "org/apache/velocity", "org/apache/momentum", null, Arrays.asList("org/apache/velocity/excluded/*"));
        assertTrue(relocator.canRelocatePath("/org/apache/velocity/mass.properties"));
        assertTrue(relocator.canRelocatePath("org/apache/velocity/mass.properties"));
        assertFalse(relocator.canRelocatePath("/org/apache/velocity/excluded/mass.properties"));
        assertFalse(relocator.canRelocatePath("org/apache/velocity/excluded/mass.properties"));
    }

    @Test
    public void testCanRelocateAbsClassPathWithIncludes() {
        SimpleRelocator relocator = new SimpleRelocator(
                "org/apache/velocity", "org/apache/momentum", Arrays.asList("org/apache/velocity/included/*"), null);
        assertFalse(relocator.canRelocatePath("/org/apache/velocity/mass.properties"));
        assertFalse(relocator.canRelocatePath("org/apache/velocity/mass.properties"));
        assertTrue(relocator.canRelocatePath("/org/apache/velocity/included/mass.properties"));
        assertTrue(relocator.canRelocatePath("org/apache/velocity/included/mass.properties"));
    }

    @Test
    public void testRelocatePath() {
        SimpleRelocator relocator;

        relocator = new SimpleRelocator("org.foo", null, null, null);
        assertEquals("hidden/org/foo/bar/Class.class", relocator.relocatePath("org/foo/bar/Class.class"));

        relocator = new SimpleRelocator("org.foo", "private.stuff", null, null);
        assertEquals("private/stuff/bar/Class.class", relocator.relocatePath("org/foo/bar/Class.class"));
    }

    @Test
    public void testRelocateClass() {
        SimpleRelocator relocator;

        relocator = new SimpleRelocator("org.foo", null, null, null);
        assertEquals("hidden.org.foo.bar.Class", relocator.relocateClass("org.foo.bar.Class"));

        relocator = new SimpleRelocator("org.foo", "private.stuff", null, null);
        assertEquals("private.stuff.bar.Class", relocator.relocateClass("org.foo.bar.Class"));
    }

    @Test
    public void testRelocateRawString() {
        SimpleRelocator relocator;

        relocator = new SimpleRelocator("Lorg/foo", "Lhidden/org/foo", null, null, true);
        assertEquals("(I)Lhidden/org/foo/bar/Class;", relocator.relocatePath("(I)Lorg/foo/bar/Class;"));

        relocator = new SimpleRelocator("^META-INF/org.foo.xml$", "META-INF/hidden.org.foo.xml", null, null, true);
        assertEquals("META-INF/hidden.org.foo.xml", relocator.relocatePath("META-INF/org.foo.xml"));
    }

    @Test
    public void testRelocateMavenFiles() {
        SimpleRelocator relocator = new SimpleRelocator(
                "META-INF/maven",
                "META-INF/shade/maven",
                null,
                Collections.singletonList("META-INF/maven/com.foo.bar/artifactId/pom.*"));
        assertFalse(relocator.canRelocatePath("META-INF/maven/com.foo.bar/artifactId/pom.properties"));
        assertFalse(relocator.canRelocatePath("META-INF/maven/com.foo.bar/artifactId/pom.xml"));
        assertTrue(relocator.canRelocatePath("META-INF/maven/com/foo/bar/artifactId/pom.properties"));
        assertTrue(relocator.canRelocatePath("META-INF/maven/com/foo/bar/artifactId/pom.xml"));
        assertTrue(relocator.canRelocatePath("META-INF/maven/com-foo-bar/artifactId/pom.properties"));
        assertTrue(relocator.canRelocatePath("META-INF/maven/com-foo-bar/artifactId/pom.xml"));
    }

    private static final String sourceFile = "package org.apache.maven.hello;\n" + "package org.objectweb.asm;\n"
            + "\n"
            + "import foo.bar.Bar;\n"
            + "import zot.baz.Baz;\n"
            + "import org.apache.maven.exclude1.Ex1;\n"
            + "import org.apache.maven.exclude1.a.b.Ex1AB;\n"
            + "import org.apache.maven.sub.exclude2.Ex2;\n"
            + "import org.apache.maven.sub.exclude2.c.d.Ex2CD;\n"
            + "import org.apache.maven.In;\n"
            + "import org.apache.maven.e.InE;\n"
            + "import org.apache.maven.f.g.InFG;\n"
            + "import java.io.IOException;\n"
            + "\n"
            + "/**\n"
            + " * Also check out {@link org.apache.maven.hello.OtherClass} and {@link\n"
            + " * org.apache.maven.hello.YetAnotherClass}\n"
            + " */\n"
            + "public class MyClass {\n"
            + "  private org.apache.maven.exclude1.x.X myX;\n"
            + "  private org.apache.maven.h.H h;\n"
            + "  private String ioInput;\n"
            + "\n"
            + "  /** Javadoc, followed by default visibility method with fully qualified return type */\n"
            + "  org.apache.maven.MyReturnType doSomething( org.apache.maven.Bar bar, org.objectweb.asm.sub.Something something) {\n"
            + "    org.apache.maven.Bar bar;\n"
            + "    org.objectweb.asm.sub.Something something;\n"
            + "    String io, val;\n"
            + "    String noRelocation = \"NoWordBoundaryXXXorg.apache.maven.In\";\n"
            + "    String relocationPackage = \"org.apache.maven.In\";\n"
            + "    String relocationPath = \"org/apache/maven/In\";\n"
            + "  }\n"
            + "}\n";

    private static final String relocatedFile = "package com.acme.maven.hello;\n" + "package aj.org.objectweb.asm;\n"
            + "\n"
            + "import foo.bar.Bar;\n"
            + "import zot.baz.Baz;\n"
            + "import org.apache.maven.exclude1.Ex1;\n"
            + "import org.apache.maven.exclude1.a.b.Ex1AB;\n"
            + "import org.apache.maven.sub.exclude2.Ex2;\n"
            + "import org.apache.maven.sub.exclude2.c.d.Ex2CD;\n"
            + "import com.acme.maven.In;\n"
            + "import com.acme.maven.e.InE;\n"
            + "import com.acme.maven.f.g.InFG;\n"
            + "import java.io.IOException;\n"
            + "\n"
            + "/**\n"
            + " * Also check out {@link com.acme.maven.hello.OtherClass} and {@link\n"
            + " * com.acme.maven.hello.YetAnotherClass}\n"
            + " */\n"
            + "public class MyClass {\n"
            + "  private org.apache.maven.exclude1.x.X myX;\n"
            + "  private com.acme.maven.h.H h;\n"
            + "  private String ioInput;\n"
            + "\n"
            + "  /** Javadoc, followed by default visibility method with fully qualified return type */\n"
            + "  com.acme.maven.MyReturnType doSomething( com.acme.maven.Bar bar, aj.org.objectweb.asm.sub.Something something) {\n"
            + "    com.acme.maven.Bar bar;\n"
            + "    aj.org.objectweb.asm.sub.Something something;\n"
            + "    String io, val;\n"
            + "    String noRelocation = \"NoWordBoundaryXXXorg.apache.maven.In\";\n"
            + "    String relocationPackage = \"com.acme.maven.In\";\n"
            + "    String relocationPath = \"com/acme/maven/In\";\n"
            + "  }\n"
            + "}\n";

    @Test
    public void testRelocateSourceWithExcludesRaw() {
        SimpleRelocator relocator = new SimpleRelocator(
                "org.apache.maven",
                "com.acme.maven",
                Arrays.asList("foo.bar", "zot.baz"),
                Arrays.asList("irrelevant.exclude", "org.apache.maven.exclude1", "org.apache.maven.sub.exclude2"),
                true);
        assertEquals(sourceFile, relocator.applyToSourceContent(sourceFile));
    }

    @Test
    public void testRelocateSourceWithExcludes() {
        // Main relocator with in-/excludes
        SimpleRelocator relocator = new SimpleRelocator(
                "org.apache.maven",
                "com.acme.maven",
                Arrays.asList("foo.bar", "zot.baz"),
                Arrays.asList("irrelevant.exclude", "org.apache.maven.exclude1", "org.apache.maven.sub.exclude2"));
        // Make sure not to replace variables 'io' and 'ioInput', package 'java.io'
        SimpleRelocator ioRelocator = new SimpleRelocator("io", "shaded.io", null, null);
        // Check corner case which was not working in PR #100
        SimpleRelocator asmRelocator = new SimpleRelocator("org.objectweb.asm", "aj.org.objectweb.asm", null, null);
        // Make sure not to replace 'foo' package by path-like 'shaded/foo'
        SimpleRelocator fooRelocator = new SimpleRelocator("foo", "shaded.foo", null, Arrays.asList("foo.bar"));
        assertEquals(
                relocatedFile,
                fooRelocator.applyToSourceContent(asmRelocator.applyToSourceContent(
                        ioRelocator.applyToSourceContent(relocator.applyToSourceContent(sourceFile)))));
    }
}
