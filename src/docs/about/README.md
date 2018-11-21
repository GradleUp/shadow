# About This Project

I started this project in December of 2012. We were working on converting from a monolithic application into the
new hot jazz of "microservices" using Dropwizard.
I had also just started learning about Gradle and I knew that the incremental build system it provided would benefit
our development team greatly.
Unfortunately, the closest thing that Gradle had to Maven's Shade plugin was its ability to create application TARs and
ZIPs.

So, Charlie Knudsen and myself set out to port the existing Shade code into a Gradle plugin.
This port is what existed up until the `0.9` milestone releases for Shadow.
It functioned, but it wasn't idiomatic Gradle by any means.

Starting with 0.9, Shadow was rewritten from the ground up as standard Gradle plugin and leveraged as much of Gradle's
classes and concepts as possible.
At the same time as the 0.9 release, Gradle was announcing the [Gradle Plugin Portal](https://plugins.gradle.org) and
so Shadow was published there.

## Maintainers

* [John Engelman](https://github.com/johnrengelman)

## Contributors

* [Charlie Knudsen](https://github.com/charliek)
* [Fedor Korotkov](https://github.com/fkorotkov)
* [Haw-Bin Chai](https://github.com/hbchai)
* [Serban Iordache](https://github.com/siordache)
* [Minecrell](https://github.com/Minecrell)
* [Matt Hurne](https://github.com/mhurne)
* [Andres Almiray](https://github.com/aalmiray)
* [Brandon Kearby](https://github.com/brandonkearby)
* [John Szakmeister](https://github.com/jszakmeister)
* [Ethan Hall](https://github.com/ethankhall)
* [Piotr Kubowicz](https://github.com/pkubowicz)
* [Marc Philipp](https://github.com/marcphilipp)
* [Rob Spieldenner](https://github.com/rspieldenner)
* [Marke Vieira](https://github.com/mark-vieira)
* [Ben Adazza](https://github.com/ben-adazza)
* [Tyler Benson](https://github.com/tylerbenson)
* [Scott Newson](https://github.com/sgnewson)
* [Martin Sadowski](https://github.com/ttsiebzehntt)
* [debanne](https://github.com/debanne)
* [Felipe Lima](https://github.com/felipecsl)
* [Paul N. Baker](https://github.com/paul-nelson-baker)
* [Chris Cowan](https://github.com/Macil)
* [Sergey Tselovalnikov](https://github.com/SerCeMan)
* [Osip Fatkullin](https://github.com/osipxd)
* [Victor Tso](https://github.com/roxchkplusony)
* [Petar Petrov](https://github.com/petarov)
* [Mark Vieira](https://github.com/mark-vieira)
