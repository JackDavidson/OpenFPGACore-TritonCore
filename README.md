Visit our [homepage!](http://jackdavidson.github.io/OpenFPGACore-TritonCore)


OpenFPGACore (TritonCore I)

This is a very simple and minimal FPGA that is implemented in CHISEL.
To experiment with this project, you will need to install sbt.

This FPGA core design is not intended to be production quality. Instead it is
meant as an experiment into open source FPGAs. This project will hopefully be
useful to those who wish to learn about how FPGAs work, and will also
hopefully serve as a seed to future open FPGA core work.

An FPGA core is not the same as an FPGA board. FPGA cores are the heart of
FPGA boards, and are one of the more complex parts of the board. The complexity
does not come from the core its self, but in how it is programmed. This
particular implementation uses look up tables (LUTs), a general routing
table (GRT) and logic cells that are each composed of a single LUT, plus an
optional register on the result. Please look at the documentation for an
in-depth explanation of each part.

This project has three components, all of which are essential:
  1. The FPGA core (this repository)
  2. The netlist generator (https://github.com/hungrymonkey/yosys-1)
  3. The bitstream generator (https://github.com/Ailss/pnr)


to build this project and run the unit tests, simply type:

  $ export SBT_OPTS="-Xmx2G -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=2G -Xss2M  -Duser.timezone=GMT"
  $ sbt run
  ** sbt_opts is because the core is too large for the default memory allocation of sbt **

to install sbt on Ubuntu, go to http://www.scala-sbt.org/download.html
follow the instructions under (deb). you can use ctrl+alt+t to open terminal,
and ctrl+shift+v to paste lines into terminal
  ** before you try running the FPGA tests **
  $ sbt
  ** allow sbt to install required items **
  ** hit ctrl+d to exit sbt **

it is also recommended that you install the IntelliJ idea ide, and its Scala plugin.
available at:

  https://www.jetbrains.com/idea/                  (intelli-J, Scala plugin option on install)



Full documentation on Tritoncore-I is available in TritoncoreI/docs/index.html
The code is also well commented and fairly clear.
