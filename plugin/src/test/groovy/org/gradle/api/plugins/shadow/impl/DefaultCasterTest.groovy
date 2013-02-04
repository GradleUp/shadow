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

package org.gradle.api.plugins.shadow.impl

import junit.framework.TestCase
import org.gradle.api.plugins.shadow.filter.Filter
import org.gradle.api.plugins.shadow.relocation.Relocator
import org.gradle.api.plugins.shadow.relocation.SimpleRelocator
import org.gradle.api.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import org.gradle.api.plugins.shadow.transformers.Transformer

/**
 * @author Jason van Zyl
 * @author Mauro Talevi
 *
 * Modified from org.apache.maven.plugins.shade.DefaultShader.java
 *
 * Modifications
 * @author John Engelman
 */
class DefaultShaderTest extends TestCase {
    private static final String[] EXCLUDES = [
            "org/codehaus/plexus/util/xml/Xpp3Dom",
            "org/codehaus/plexus/util/xml/pull.*"] as String[]

    void testShaderWithDefaultShadedPattern() {
        shaderWithPattern(null, File.createTempFile("foo-default", "jar"), EXCLUDES)
    }

    void testShaderWithStaticInitializedClass() {
        Caster s = newCaster()

        List<File> jars = []

        jars.add(new File("src/test/jars/test-artifact-1.0-SNAPSHOT.jar"))

        List<Relocator> relocators = new ArrayList<Relocator>()

        relocators.add(new SimpleRelocator("org.apache.maven.plugins.shade", null, null, null))

        List<Transformer> resourceTransformers = new ArrayList<Transformer>()

        List<Filter> filters = new ArrayList<Filter>()

        File file = File.createTempFile("testShaderWithStaticInitializedClass", "jar")

        ShadowRequest shadowRequest = new ShadowRequest()
        shadowRequest.setJars(jars)
        shadowRequest.setUberJar(file)
        shadowRequest.setFilters(filters)
        shadowRequest.setRelocators(relocators)
        shadowRequest.setResourceTransformers(resourceTransformers)

        s.cast(shadowRequest)

        URLClassLoader cl = new URLClassLoader([file.toURI().toURL()] as URL[])
        Class<?> c = cl.loadClass("hidden.org.apache.maven.plugins.shade.Lib")
        Object o = c.newInstance()
        assertEquals("foo.bar/baz", c.getDeclaredField("CONSTANT").get(o))
    }

    void testShaderWithCustomShadedPattern() {
        shaderWithPattern("org/shaded/plexus/util", File.createTempFile("foo-custom", "jar"), EXCLUDES)
    }

    void testShaderWithoutExcludesShouldRemoveReferencesOfOriginalPattern() {
        //FIXME:  shaded jar should not include references to org/codehaus/* (empty dirs) or org.codehaus.* META-INF files.
        shaderWithPattern("org/shaded/plexus/util", File.createTempFile("foo-custom-without-excludes", "jar"), [] as String[])
    }

    void shaderWithPattern(String shadedPattern, File jar, String[] excludes) {
        DefaultCaster s = newCaster()

        List<File> jars = []

        jars.add(new File("src/test/jars/test-project-1.0-SNAPSHOT.jar"))

        jars.add(new File("src/test/jars/plexus-utils-1.4.1.jar"))

        List<Relocator> relocators = new ArrayList<Relocator>()

        relocators.add(new SimpleRelocator("org/codehaus/plexus/util", shadedPattern, null, Arrays.asList(excludes)))

        List<Transformer> resourceTransformers = new ArrayList<Transformer>()

        resourceTransformers.add(new ComponentsXmlResourceTransformer())

        List<Filter> filters = new ArrayList<Filter>()

        ShadowRequest shadowRequest = new ShadowRequest()
        shadowRequest.setJars(jars)
        shadowRequest.setUberJar(jar)
        shadowRequest.setFilters(filters)
        shadowRequest.setRelocators(relocators)
        shadowRequest.setResourceTransformers(resourceTransformers)

        s.cast(shadowRequest)
    }

    private static DefaultCaster newCaster() {
        DefaultCaster s = new DefaultCaster()

        return s
    }

}
