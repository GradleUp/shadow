package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.impl.RelocatorRemapper
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class RelocatorRemapperSpec extends Specification {

    def "mapValue relocates #descriptor correctly"() {
        given:
        Relocator relocator = new SimpleRelocator('org.package', 'shadow.org.package', [], [])
        RelocatorRemapper remapper = new RelocatorRemapper([relocator], new ShadowStats())

        expect:
        remapper.mapValue(descriptor) == expected

        where:
        descriptor                                               || expected
        'Lorg/package/ClassA;'                                   || 'Lshadow/org/package/ClassA;'
        '[Lorg/package/ClassA;'                                  || '[Lshadow/org/package/ClassA;'
        '(Lorg/package/ClassA;)V'                                || '(Lshadow/org/package/ClassA;)V'
        '(ZLorg/package/ClassA;)V'                               || '(ZLshadow/org/package/ClassA;)V'
        '(ILorg/package/ClassA;Lorg/package/ClassB;)V'           || '(ILshadow/org/package/ClassA;Lshadow/org/package/ClassB;)V'
        'Lorg/package/ClassA;Lorg/package/ClassB;'               || 'Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;'
        '()Lorg/package/ClassA;'                                 || '()Lshadow/org/package/ClassA;'
    }

    def "skipStringConstants is per relocator in mapValue"() {
        given:
        Relocator skippingRelocator = new SimpleRelocator('org.package', 'shadow.org.package', [], [], true)
        Relocator normalRelocator = new SimpleRelocator('com.example', 'shadow.com.example', [], [])
        RelocatorRemapper remapper = new RelocatorRemapper([skippingRelocator, normalRelocator], new ShadowStats())

        expect:
        remapper.mapValue('Lcom/example/Foo;') == 'Lshadow/com/example/Foo;'
        remapper.mapValue('Lorg/package/Bar;') == 'Lorg/package/Bar;'
    }
}
