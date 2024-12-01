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
package com.github.jengelman.gradle.plugins.shadow.relocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test for [SimpleRelocator].
 *
 * @author Benjamin Bentmann
 */
class SimpleRelocatorTestNew {
  @Test
  fun testNoNpeRelocateClass() {
    SimpleRelocator("foo", "bar", null, null, true).relocateClass("foo")
  }

  @Test
  fun testCanRelocatePath() {
    var relocator = SimpleRelocator("org.foo", null, null, null)
    assertTrue(relocator.canRelocatePath("org/foo/Class"))
    assertTrue(relocator.canRelocatePath("org/foo/Class.class"))
    assertTrue(relocator.canRelocatePath("org/foo/bar/Class"))
    assertTrue(relocator.canRelocatePath("org/foo/bar/Class.class"))
    assertFalse(relocator.canRelocatePath("com/foo/bar/Class"))
    assertFalse(relocator.canRelocatePath("com/foo/bar/Class.class"))
    assertFalse(relocator.canRelocatePath("org/Foo/Class"))
    assertFalse(relocator.canRelocatePath("org/Foo/Class.class"))

    relocator = SimpleRelocator(
      "org.foo",
      null,
      null,
      mutableListOf<String>("org.foo.Excluded", "org.foo.public.*", "org.foo.Public*Stuff"),
    )
    assertTrue(relocator.canRelocatePath("org/foo/Class"))
    assertTrue(relocator.canRelocatePath("org/foo/Class.class"))
    assertTrue(relocator.canRelocatePath("org/foo/excluded"))
    assertFalse(relocator.canRelocatePath("org/foo/Excluded"))
    assertFalse(relocator.canRelocatePath("org/foo/Excluded.class"))
    assertFalse(relocator.canRelocatePath("org/foo/public"))
    assertFalse(relocator.canRelocatePath("org/foo/public/Class"))
    assertFalse(relocator.canRelocatePath("org/foo/public/Class.class"))
    assertTrue(relocator.canRelocatePath("org/foo/publicRELOC/Class"))
    assertTrue(relocator.canRelocatePath("org/foo/PrivateStuff"))
    assertTrue(relocator.canRelocatePath("org/foo/PrivateStuff.class"))
    assertFalse(relocator.canRelocatePath("org/foo/PublicStuff"))
    assertFalse(relocator.canRelocatePath("org/foo/PublicStuff.class"))
    assertFalse(relocator.canRelocatePath("org/foo/PublicUtilStuff"))
    assertFalse(relocator.canRelocatePath("org/foo/PublicUtilStuff.class"))
  }

  @Test
  fun testCanRelocateClass() {
    var relocator = SimpleRelocator("org.foo", null, null, null)
    assertTrue(relocator.canRelocateClass("org.foo.Class"))
    assertTrue(relocator.canRelocateClass("org.foo.bar.Class"))
    assertFalse(relocator.canRelocateClass("com.foo.bar.Class"))
    assertFalse(relocator.canRelocateClass("org.Foo.Class"))

    relocator = SimpleRelocator(
      "org.foo",
      null,
      null,
      mutableListOf<String>("org.foo.Excluded", "org.foo.public.*", "org.foo.Public*Stuff"),
    )
    assertTrue(relocator.canRelocateClass("org.foo.Class"))
    assertTrue(relocator.canRelocateClass("org.foo.excluded"))
    assertFalse(relocator.canRelocateClass("org.foo.Excluded"))
    assertFalse(relocator.canRelocateClass("org.foo.public"))
    assertFalse(relocator.canRelocateClass("org.foo.public.Class"))
    assertTrue(relocator.canRelocateClass("org.foo.publicRELOC.Class"))
    assertTrue(relocator.canRelocateClass("org.foo.PrivateStuff"))
    assertFalse(relocator.canRelocateClass("org.foo.PublicStuff"))
    assertFalse(relocator.canRelocateClass("org.foo.PublicUtilStuff"))
  }

  @Test
  fun testCanRelocateRawString() {
    var relocator = SimpleRelocator("org/foo", null, null, null, true)
    assertTrue(relocator.canRelocatePath("(I)org/foo/bar/Class;"))

    relocator = SimpleRelocator("^META-INF/org.foo.xml$", null, null, null, true)
    assertTrue(relocator.canRelocatePath("META-INF/org.foo.xml"))
  }

  // MSHADE-119, make sure that the easy part of this works.
  @Test
  fun testCanRelocateAbsClassPath() {
    val relocator = SimpleRelocator("org.apache.velocity", "org.apache.momentum", null, null)
    assertEquals(
      "/org/apache/momentum/mass.properties",
      relocator.relocatePath("/org/apache/velocity/mass.properties"),
    )
  }

  @Test
  fun testCanRelocateAbsClassPathWithExcludes() {
    val relocator = SimpleRelocator(
      "org/apache/velocity",
      "org/apache/momentum",
      null,
      mutableListOf<String>("org/apache/velocity/excluded/*"),
    )
    assertTrue(relocator.canRelocatePath("/org/apache/velocity/mass.properties"))
    assertTrue(relocator.canRelocatePath("org/apache/velocity/mass.properties"))
    assertFalse(relocator.canRelocatePath("/org/apache/velocity/excluded/mass.properties"))
    assertFalse(relocator.canRelocatePath("org/apache/velocity/excluded/mass.properties"))
  }

  @Test
  fun testCanRelocateAbsClassPathWithIncludes() {
    val relocator = SimpleRelocator(
      "org/apache/velocity",
      "org/apache/momentum",
      mutableListOf<String>("org/apache/velocity/included/*"),
      null,
    )
    assertFalse(relocator.canRelocatePath("/org/apache/velocity/mass.properties"))
    assertFalse(relocator.canRelocatePath("org/apache/velocity/mass.properties"))
    assertTrue(relocator.canRelocatePath("/org/apache/velocity/included/mass.properties"))
    assertTrue(relocator.canRelocatePath("org/apache/velocity/included/mass.properties"))
  }

  @Test
  fun testRelocatePath() {
    var relocator = SimpleRelocator("org.foo", null, null, null)
    assertEquals("hidden/org/foo/bar/Class.class", relocator.relocatePath("org/foo/bar/Class.class"))

    relocator = SimpleRelocator("org.foo", "private.stuff", null, null)
    assertEquals("private/stuff/bar/Class.class", relocator.relocatePath("org/foo/bar/Class.class"))
  }

  @Test
  fun testRelocateClass() {
    var relocator = SimpleRelocator("org.foo", null, null, null)
    assertEquals("hidden.org.foo.bar.Class", relocator.relocateClass("org.foo.bar.Class"))

    relocator = SimpleRelocator("org.foo", "private.stuff", null, null)
    assertEquals("private.stuff.bar.Class", relocator.relocateClass("org.foo.bar.Class"))
  }

  @Test
  fun testRelocateRawString() {
    var relocator = SimpleRelocator("Lorg/foo", "Lhidden/org/foo", null, null, true)
    assertEquals("(I)Lhidden/org/foo/bar/Class;", relocator.relocatePath("(I)Lorg/foo/bar/Class;"))

    relocator = SimpleRelocator("^META-INF/org.foo.xml$", "META-INF/hidden.org.foo.xml", null, null, true)
    assertEquals("META-INF/hidden.org.foo.xml", relocator.relocatePath("META-INF/org.foo.xml"))
  }

  @Test
  fun testRelocateMavenFiles() {
    val relocator = SimpleRelocator(
      "META-INF/maven",
      "META-INF/shade/maven",
      null,
      mutableListOf<String>("META-INF/maven/com.foo.bar/artifactId/pom.*"),
    )
    assertFalse(relocator.canRelocatePath("META-INF/maven/com.foo.bar/artifactId/pom.properties"))
    assertFalse(relocator.canRelocatePath("META-INF/maven/com.foo.bar/artifactId/pom.xml"))
    assertTrue(relocator.canRelocatePath("META-INF/maven/com/foo/bar/artifactId/pom.properties"))
    assertTrue(relocator.canRelocatePath("META-INF/maven/com/foo/bar/artifactId/pom.xml"))
    assertTrue(relocator.canRelocatePath("META-INF/maven/com-foo-bar/artifactId/pom.properties"))
    assertTrue(relocator.canRelocatePath("META-INF/maven/com-foo-bar/artifactId/pom.xml"))
  }

  @Test
  fun testRelocateSourceWithExcludesRaw() {
    val relocator = SimpleRelocator(
      "org.apache.maven",
      "com.acme.maven",
      mutableListOf<String>("foo.bar", "zot.baz"),
      mutableListOf<String>("irrelevant.exclude", "org.apache.maven.exclude1", "org.apache.maven.sub.exclude2"),
      true,
    )
    assertEquals(sourceFile, relocator.applyToSourceContent(sourceFile))
  }

  @Test
  fun testRelocateSourceWithExcludes() {
    // Main relocator with in-/excludes
    val relocator = SimpleRelocator(
      "org.apache.maven",
      "com.acme.maven",
      mutableListOf<String>("foo.bar", "zot.baz"),
      mutableListOf<String>("irrelevant.exclude", "org.apache.maven.exclude1", "org.apache.maven.sub.exclude2"),
    )
    // Make sure not to replace variables 'io' and 'ioInput', package 'java.io'
    val ioRelocator = SimpleRelocator("io", "shaded.io", null, null)
    // Check corner case which was not working in PR #100
    val asmRelocator = SimpleRelocator("org.objectweb.asm", "aj.org.objectweb.asm", null, null)
    // Make sure not to replace 'foo' package by path-like 'shaded/foo'
    val fooRelocator = SimpleRelocator("foo", "shaded.foo", null, mutableListOf<String>("foo.bar"))
    assertEquals(
      relocatedFile,
      fooRelocator.applyToSourceContent(
        asmRelocator.applyToSourceContent(
          ioRelocator.applyToSourceContent(relocator.applyToSourceContent(sourceFile)),
        ),
      ),
    )
  }

  companion object {
    private val sourceFile = """
      package org.apache.maven.hello;
      package org.objectweb.asm;

      import foo.bar.Bar;
      import zot.baz.Baz;
      import org.apache.maven.exclude1.Ex1;
      import org.apache.maven.exclude1.a.b.Ex1AB;
      import org.apache.maven.sub.exclude2.Ex2;
      import org.apache.maven.sub.exclude2.c.d.Ex2CD;
      import org.apache.maven.In;
      import org.apache.maven.e.InE;
      import org.apache.maven.f.g.InFG;
      import java.io.IOException;

      /**
       * Also check out {@link org.apache.maven.hello.OtherClass} and {@link
       * org.apache.maven.hello.YetAnotherClass}
       */
      public class MyClass {
        private org.apache.maven.exclude1.x.X myX;
        private org.apache.maven.h.H h;
        private String ioInput;

        /** Javadoc, followed by default visibility method with fully qualified return type */
        org.apache.maven.MyReturnType doSomething( org.apache.maven.Bar bar, org.objectweb.asm.sub.Something something) {
          org.apache.maven.Bar bar;
          org.objectweb.asm.sub.Something something;
          String io, val;
          String noRelocation = "NoWordBoundaryXXXorg.apache.maven.In";
          String relocationPackage = "org.apache.maven.In";
          String relocationPath = "org/apache/maven/In";
        }
      }
    """.trimIndent()

    private val relocatedFile = """
      package com.acme.maven.hello;
      package aj.org.objectweb.asm;

      import foo.bar.Bar;
      import zot.baz.Baz;
      import org.apache.maven.exclude1.Ex1;
      import org.apache.maven.exclude1.a.b.Ex1AB;
      import org.apache.maven.sub.exclude2.Ex2;
      import org.apache.maven.sub.exclude2.c.d.Ex2CD;
      import com.acme.maven.In;
      import com.acme.maven.e.InE;
      import com.acme.maven.f.g.InFG;
      import java.io.IOException;

      /**
       * Also check out {@link com.acme.maven.hello.OtherClass} and {@link
       * com.acme.maven.hello.YetAnotherClass}
       */
      public class MyClass {
        private org.apache.maven.exclude1.x.X myX;
        private com.acme.maven.h.H h;
        private String ioInput;

        /** Javadoc, followed by default visibility method with fully qualified return type */
        com.acme.maven.MyReturnType doSomething( com.acme.maven.Bar bar, aj.org.objectweb.asm.sub.Something something) {
          com.acme.maven.Bar bar;
          aj.org.objectweb.asm.sub.Something something;
          String io, val;
          String noRelocation = "NoWordBoundaryXXXorg.apache.maven.In";
          String relocationPackage = "com.acme.maven.In";
          String relocationPath = "com/acme/maven/In";
        }
      }
    """.trimIndent()

    private fun SimpleRelocator.relocatePath(path: String): String {
      return relocatePath(RelocatePathContext.builder().path(path).build())
    }

    private fun SimpleRelocator.relocateClass(className: String): String {
      return relocateClass(RelocateClassContext.builder().className(className).build())
    }
  }
}
