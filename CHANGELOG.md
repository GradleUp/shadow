# Change Log

## [Unreleased]

**Changed**

- Bump the min Gradle requirement from `8.0.0` to `8.3`. ([#876](https://github.com/johnrengelman/shadow/pull/876))
- Support Java 21. ([#876](https://github.com/johnrengelman/shadow/pull/876))
- Use new file permission API from Gradle 8.3. ([#876](https://github.com/johnrengelman/shadow/pull/876))

**Fixed**

- Fix for PropertiesFileTransformer breaks Reproducible builds in
  8.1.1 ([#858](https://github.com/johnrengelman/shadow/pull/858))

## [8.1.1] - 2023-03-21

### What's Changed

* Replace deprecated ConfigureUtil by @Goooler in https://github.com/johnrengelman/shadow/pull/826
* Polish outdated configs by @Goooler in https://github.com/johnrengelman/shadow/pull/831
* Update plugin com.gradle.enterprise to v3.12.5 by @renovate in https://github.com/johnrengelman/shadow/pull/838
* Update dependency gradle to v8.0.2 by @renovate in https://github.com/johnrengelman/shadow/pull/844
* fix(deps): update dependency org.codehaus.plexus:plexus-utils to v3.5.1 by @renovate
  in https://github.com/johnrengelman/shadow/pull/837
* chore(deps): update dependency prismjs to v1.27.0 [security] by @renovate
  in https://github.com/johnrengelman/shadow/pull/828
* Encode transformed properties files with specified Charset by @scottsteen
  in https://github.com/johnrengelman/shadow/pull/819
* chore(deps): update dependency vuepress to v1.9.9 by @renovate in https://github.com/johnrengelman/shadow/pull/842

### New Contributors

* @renovate made their first contribution in https://github.com/johnrengelman/shadow/pull/838
* @scottsteen made their first contribution in https://github.com/johnrengelman/shadow/pull/819

**Full Changelog**: https://github.com/johnrengelman/shadow/compare/8.1.0...8.1.1

## [8.1.0] - 2023-02-27

### What's Changed

* Minor cleanups by @Goooler in https://github.com/johnrengelman/shadow/pull/823
* Support config cache by @Goooler in https://github.com/johnrengelman/shadow/pull/824
* Fix RelocatorRemapper: do not map inner class name if not changed by @Him188
  in https://github.com/johnrengelman/shadow/pull/793

### New Contributors

* @Him188 made their first contribution in https://github.com/johnrengelman/shadow/pull/793

**Full Changelog**: https://github.com/johnrengelman/shadow/compare/8.0.0...8.1.0

## [8.0.0] - 2023-02-25

### What's Changed

* Fix the plugin dependency identifier in the docs by @lnhrdt
  in [#754](https://github.com/johnrengelman/shadow/pull/754)
* mergeGroovyExtensionModules() not working with Groovy 2.5+ by @paulk-asert
  in [#779](https://github.com/johnrengelman/shadow/pull/779)
* Upgrade to ASM 9.3 to support JDK 19. by @vyazelenko in https://github.com/johnrengelman/shadow/pull/770
* Do not add a dependencies block if it's already there by @desiderantes
  in https://github.com/johnrengelman/shadow/pull/769
* Update README with new badge and links by @ThexXTURBOXx in https://github.com/johnrengelman/shadow/pull/743
* Fix value not set when rawString is true. by @qian0817 in https://github.com/johnrengelman/shadow/pull/765
* Mark the Log4j2PluginsCacheFileTransformer as cacheable. by @staktrace
  in https://github.com/johnrengelman/shadow/pull/724
* Fix retrieval of dependencies node when publishing by @netomi in https://github.com/johnrengelman/shadow/pull/798
* Upgrade dependency ASM from `9.3` to `9.4` by @codecholeric in https://github.com/johnrengelman/shadow/pull/817
* Fix a typo of code comment in the minimizing page by @jebnix in https://github.com/johnrengelman/shadow/pull/800
* Prefer using plugin extensions over deprecated conventions by @eskatos
  in https://github.com/johnrengelman/shadow/pull/821
* Introduce CleanProperties by @simPod in https://github.com/johnrengelman/shadow/pull/622
* Support Gradle 8.0 by @Goooler in https://github.com/johnrengelman/shadow/pull/822
* Updated dependencies , Gradle versions and Fix Test by @ElisaMin in https://github.com/johnrengelman/shadow/pull/791

### New Contributors

* @lnhrdt made their first contribution in https://github.com/johnrengelman/shadow/pull/754
* @paulk-asert made their first contribution in https://github.com/johnrengelman/shadow/pull/779
* @desiderantes made their first contribution in https://github.com/johnrengelman/shadow/pull/769
* @ThexXTURBOXx made their first contribution in https://github.com/johnrengelman/shadow/pull/743
* @qian0817 made their first contribution in https://github.com/johnrengelman/shadow/pull/765
* @staktrace made their first contribution in https://github.com/johnrengelman/shadow/pull/724
* @netomi made their first contribution in https://github.com/johnrengelman/shadow/pull/798
* @codecholeric made their first contribution in https://github.com/johnrengelman/shadow/pull/817
* @jebnix made their first contribution in https://github.com/johnrengelman/shadow/pull/800
* @eskatos made their first contribution in https://github.com/johnrengelman/shadow/pull/821
* @simPod made their first contribution in https://github.com/johnrengelman/shadow/pull/622
* @Goooler made their first contribution in https://github.com/johnrengelman/shadow/pull/822
* @ElisaMin made their first contribution in https://github.com/johnrengelman/shadow/pull/791

**Full Changelog**: https://github.com/johnrengelman/shadow/compare/7.1.2...8.0.0


[Unreleased]: https://github.com/GradleUp/shadow/compare/8.1.1...HEAD
[8.1.1]: https://github.com/GradleUp/shadow/releases/tag/8.1.1
[8.1.0]: https://github.com/GradleUp/shadow/releases/tag/8.1.0
[8.0.0]: https://github.com/GradleUp/shadow/releases/tag/8.0.0
