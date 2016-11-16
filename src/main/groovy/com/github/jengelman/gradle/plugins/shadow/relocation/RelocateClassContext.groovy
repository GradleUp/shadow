package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import groovy.transform.Canonical
import groovy.transform.builder.Builder

@Canonical
@Builder
class RelocateClassContext {

    String className
    ShadowStats stats

}
