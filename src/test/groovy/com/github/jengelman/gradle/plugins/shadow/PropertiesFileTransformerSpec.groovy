package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import spock.lang.Issue
import spock.lang.Unroll

class PropertiesFileTransformerSpec extends PluginSpecification {

    @Unroll
    def 'merge properties with different strategies: #mergeStrategy'() {
        given:
        File one = buildJar('one.jar')
            .insertFile('test.properties',
                'key1=val1\nkey2=val2\nkey3=val3').write()

        File two = buildJar('two.jar')
            .insertFile('test.properties',
                'key1=VAL1\nkey2=VAL2\nkey4=val4').write()

        buildFile << """
            import ${PropertiesFileTransformer.name}
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                transform(PropertiesFileTransformer) {
                    paths = ['test.properties']
                    mergeStrategy = '${mergeStrategy}'
                }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'test.properties')
        def lines = text.replace('#', '').trim().split("\\r?\\n").toList()
        switch (mergeStrategy) {
            case 'first':
                assert lines.size() == 4
                assert lines.containsAll(['key1=val1', 'key2=val2', 'key3=val3', 'key4=val4'])
                break
            case 'latest':
                assert lines.size() == 4
                assert lines.containsAll(['key1=VAL1', 'key2=VAL2', 'key3=val3', 'key4=val4'])
                break
            case 'append':
                assert lines.size() == 4
                assert lines.containsAll(['key1=val1,VAL1', 'key2=val2,VAL2', 'key3=val3', 'key4=val4'])
                break
            default:
                assert false : "Unknown mergeStrategy: $mergeStrategy"
        }

        where:
        mergeStrategy << ['first', 'latest', 'append']
    }

    def 'merge properties with key transformer'() {
        given:
        File one = buildJar('one.jar')
            .insertFile('META-INF/test.properties', 'foo=bar')
            .write()

        File two = buildJar('two.jar')
            .insertFile('META-INF/test.properties', 'FOO=baz')
            .write()

        buildFile << """
            import ${PropertiesFileTransformer.name}
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                transform(PropertiesFileTransformer) {
                    paths = ['META-INF/test.properties']
                    mergeStrategy = 'append'
                    keyTransformer = { key -> key.toUpperCase() }
                }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        output.exists()
        String text = getJarFileContents(output, 'META-INF/test.properties')
        text.contains('FOO=bar,baz')
    }

    def 'merge properties with specified charset'() {
        given:
        File one = buildJar('one.jar')
            .insertFile('META-INF/utf8.properties', 'foo=第一')
            .write()

        File two = buildJar('two.jar')
            .insertFile('META-INF/utf8.properties', 'foo=第二')
            .write()

        buildFile << """
            import ${PropertiesFileTransformer.name}
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                transform(PropertiesFileTransformer) {
                    paths = ['META-INF/utf8.properties']
                    mergeStrategy = 'append'
                    charset = 'UTF-8'
                }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        output.exists()
        String text = getJarFileContents(output, 'META-INF/utf8.properties')
        text.contains('foo=第一,第二')
    }

    def 'merge properties with mappings'() {
        given:
        File one = buildJar('one.jar')
            .insertFile('META-INF/foo.properties', 'foo=1')
            .insertFile('META-INF/bar.properties', 'bar=2')
            .write()

        File two = buildJar('two.jar')
            .insertFile('META-INF/foo.properties', 'foo=3')
            .insertFile('META-INF/bar.properties', 'bar=4')
            .write()

        buildFile << """
            import ${PropertiesFileTransformer.name}
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                transform(PropertiesFileTransformer) {
                    mappings = [
                        'META-INF/foo.properties': [mergeStrategy: 'append', mergeSeparator: ';'],
                        'META-INF/bar.properties': [mergeStrategy: 'latest']
                    ]
                }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        output.exists()

        and:
        String fooText = getJarFileContents(output, 'META-INF/foo.properties')
        fooText.contains('foo=1;3')

        and:
        String barText = getJarFileContents(output, 'META-INF/bar.properties')
        barText.contains('bar=4')
    }

    @Issue(
        ['https://github.com/GradleUp/shadow/issues/622',
            'https://github.com/GradleUp/shadow/issues/856'
        ]
    )
    def 'merged properties dont contain date comment'() {
        given:
        File one = buildJar('one.jar')
            .insertFile('META-INF/test.properties', 'foo=one')
            .write()

        File two = buildJar('two.jar')
            .insertFile('META-INF/test.properties', 'foo=two')
            .write()

        buildFile << """
            import ${PropertiesFileTransformer.name}
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                transform(PropertiesFileTransformer) {
                    paths = ['META-INF/test.properties']
                    mergeStrategy = 'append'
                }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        output.exists()
        String text = getJarFileContents(output, 'META-INF/test.properties')
        text.trim() == '#\n\nfoo=one,two'
    }
}
