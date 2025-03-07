# About This Project

I started this project in December 2012. We were working on converting from a monolithic application into the
new hot jazz of "microservices" using Dropwizard.
I had also just started learning about Gradle and I knew that the incremental build system it provided would benefit
our development team greatly.
Unfortunately, the closest thing that Gradle had to Maven's Shade plugin was its ability to create application TARs and
ZIPs.

So, Charlie Knudsen and I (John Engelman) set out to port the existing Shade code into a Gradle plugin.
This port is what existed up until the `0.9` milestone releases for Shadow.
It functioned, but it wasn't idiomatic Gradle by any means.

Starting with 0.9, Shadow was rewritten from the ground up as standard Gradle plugin and leveraged as much of Gradle's
classes and concepts as possible.
At the same time as the 0.9 release, Gradle was announcing the [Gradle Plugin Portal](https://plugins.gradle.org) and
so Shadow was published there.

## Maintainers

* [John Engelman](https://github.com/johnrengelman)

## Contributors

<a href="https://github.com/GradleUp/shadow/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=GradleUp/shadow"  alt="Contributors"/>
</a>
