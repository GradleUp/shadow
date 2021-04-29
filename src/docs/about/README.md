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

* [Alan D. Cabrera](https://github.com/maguro)
* [Andres Almiray](https://github.com/aalmiray)
* [Artem Chubaryan](https://github.com/Armaxis)
* [Attila Kelemen](https://github.com/kelemen)
* [Ben Adazza](https://github.com/ben-adazza)
* [Bernie Schelberg](https://github.com/bschelberg)
* [Brandon Kearby](https://github.com/brandonkearby)
* [Brane F. Gračnar](https://github.com/bfg)
* [Caleb Larsen](https://github.com/MuffinTheMan)
* [Charlie Knudsen](https://github.com/charliek)
* [Chris Cowan](https://github.com/Macil)
* [Chris Rankin](https://github.com/chrisr3)
* [Christian Stein](https://github.com/sormuras)
* [Daniel Oakey](https://github.com/danieloakey)
* [debanne](https://github.com/debanne)
* [Dennis Schumann](https://github.com/Hillkorn)
* [Dmitry Vyazelenko](https://github.com/vyazelenko)
* [ejjcase](https://github.com/ejjcase)
* [Ethan Hall](https://github.com/ethankhall)
* [Fedor Korotkov](https://github.com/fkorotkov)
* [Felipe Lima](https://github.com/felipecsl)
* [Gary Hale](https://github.com/ghale)
* [Haw-Bin Chai](https://github.com/hbchai)
* [Helder Pereira](https://github.com/helfper)
* [Inez Korczyński](https://github.com/inez)
* [James Nelson](https://github.com/JamesXNelson)
* [Jeff Adler](https://github.com/jeffalder)
* [John Szakmeister](https://github.com/jszakmeister)
* [Konstantin Gribov](https://github.com/grossws)
* [Lai Jiang](https://github.com/jianglai)
* [Marc Philipp](https://github.com/marcphilipp)
* [Mark Vieira](https://github.com/mark-vieira)
* [Marke Vieira](https://github.com/mark-vieira)
* [Martin Sadowski](https://github.com/ttsiebzehntt)
* [Matt Hurne](https://github.com/mhurne)
* [Matt King](https://github.com/kyrrigle)
* [Matthew Haughton](https://github.com/3flex)
* [Maximilian Müller](https://github.com/maxm123)
* [Minecrell](https://github.com/Minecrell)
* [Min-Ken Lai](https://github.com/minkenlai)
* [Nicolas Humblot](https://github.com/nhumblot)
* [Osip Fatkullin](https://github.com/osipxd)
* [Paul N. Baker](https://github.com/paul-nelson-baker)
* [Petar Petrov](https://github.com/petarov)
* [Piotr Kubowicz](https://github.com/pkubowicz)
* [Richard Marbach](https://github.com/RichardMarbach)
* [Rob Spieldenner](https://github.com/rspieldenner)
* [Roberto Perez Alcolea](https://github.com/rpalcolea)
* [Schalk W. Cronjé](https://github.com/ysb33r)
* [Scott Newson](https://github.com/sgnewson)
* [Serban Iordache](https://github.com/siordache)
* [Sergey Tselovalnikov](https://github.com/SerCeMan)
* [Tim Yates](https://github.com/timyates)
* [Trask Stalnaker](https://github.com/trask)
* [Tyler Benson](https://github.com/tylerbenson)
* [Victor Tso](https://github.com/roxchkplusony)
* [Yahor Berdnikau](https://github.com/Tapchicoma)
