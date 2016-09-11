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

import spock.lang.Unroll

import static groovy.lang.Closure.IDENTITY

@Unroll
class PropertiesFileTransformerSpec extends TransformerSpecSupport {

    void "Path #path #transform transformed"() {
        given:
        Transformer transformer = new PropertiesFileTransformer()

        when:
        boolean actual = transformer.canTransformResource(getFileElement(path))

        then:
        actual == expected

        where:
        path                 || expected
        'foo.properties'     || true
        'foo/bar.properties' || true
        'foo.props'          || false

        transform = expected ? 'can be' : 'can not be'
    }

    void exerciseAllTransformConfigurations() {
        given:
        def element = getFileElement(path)
        Transformer transformer = new PropertiesFileTransformer()
        transformer.mergeStrategy = mergeStrategy
        transformer.mergeSeparator = mergeSeparator

        when:
        if (transformer.canTransformResource(element)) {
            transformer.transform(context(path, input1))
            transformer.transform(context(path, input2))
        }

        then:
        output == toMap(transformer.propertiesEntries[path])

        where:
        path           | mergeStrategy | mergeSeparator | input1         | input2         || output
        'f.properties' | 'first'       | ''             | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo']
        'f.properties' | 'latest'      | ''             | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'bar']
        'f.properties' | 'append'      | ','            | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo,bar']
        'f.properties' | 'append'      | ';'            | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo;bar']
    }

    void exerciseAllTransformConfigurationsWithPaths() {
        given:
        def element = getFileElement(path)
        Transformer transformer = new PropertiesFileTransformer()
        transformer.paths = paths
        transformer.mergeStrategy = 'first'

        when:
        if (transformer.canTransformResource(element)) {
            transformer.transform(context(path, input1))
            transformer.transform(context(path, input2))
        }

        then:
        output == toMap(transformer.propertiesEntries[path])

        where:
        path             | paths             | input1         | input2         || output
        'f.properties'   | ['f.properties']  | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo']
        'foo.properties' | ['.*.properties'] | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo']
        'foo.properties' | ['.*bar']         | ['foo': 'foo'] | ['foo': 'bar'] || [:]
        'foo.properties' | []                | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo']
    }

    void exerciseAllTransformConfigurationsWithMappings() {
        given:
        def element = getFileElement(path)
        Transformer transformer = new PropertiesFileTransformer()
        transformer.mappings = mappings
        transformer.mergeStrategy = 'latest'

        when:
        if (transformer.canTransformResource(element)) {
            transformer.transform(context(path, input1))
            transformer.transform(context(path, input2))
        }

        then:
        output == toMap(transformer.propertiesEntries[path])

        where:
        path             | mappings                                                         | input1         | input2         || output
        'f.properties'   | ['f.properties': [mergeStrategy: 'first']]                       | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo']
        'f.properties'   | ['f.properties': [mergeStrategy: 'latest']]                      | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'bar']
        'f.properties'   | ['f.properties': [mergeStrategy: 'append']]                      | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo,bar']
        'f.properties'   | ['f.properties': [mergeStrategy: 'append', mergeSeparator: ';']] | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo;bar']
        'foo.properties' | ['.*.properties': [mergeStrategy: 'first']]                      | ['foo': 'foo'] | ['foo': 'bar'] || ['foo': 'foo']
        'foo.properties' | ['.*bar': [mergeStrategy: 'first']]                              | ['foo': 'foo'] | ['foo': 'bar'] || [:]
    }

    void appliesKeyTransformer() {
        given:
        def element = getFileElement(path)
        Transformer transformer = new PropertiesFileTransformer()
        transformer.keyTransformer = keyTransformer
        transformer.mergeStrategy = 'append'

        when:
        if (transformer.canTransformResource(element)) {
            transformer.transform(context(path, input1))
            transformer.transform(context(path, input2))
        }

        then:
        output == toMap(transformer.propertiesEntries[path])

        where:
        path             | keyTransformer                                | input1         | input2         || output
        'foo.properties' | IDENTITY                                      | ['foo': 'bar'] | ['FOO': 'baz'] || ['foo': 'bar', 'FOO': 'baz']
        'foo.properties' | { key -> key.toUpperCase() }                  | ['foo': 'bar'] | ['FOO': 'baz'] || ['FOO': 'bar,baz']
        'foo.properties' | { key -> 'bar.' + key.toLowerCase() }         | ['foo': 'bar'] | ['FOO': 'baz'] || ['bar.foo': 'bar,baz']
        'foo.properties' | { key -> key.replaceAll('^(foo)', 'bar.$1') } | ['foo': 'bar'] | ['FOO': 'baz'] || ['bar.foo': 'bar', 'FOO': 'baz']
    }
}
