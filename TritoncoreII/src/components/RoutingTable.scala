import Chisel._

class RoutingTable extends Module {
  val io = new Bundle {
    val input      = Bits(INPUT,   128)
    val routing    = Bits(INPUT,  1100)
    val outputs    = Bits(OUTPUT,  220)
  }

  io.outputs := UInt(0) // weird CHISEL req

  for (i <- 0 to 3) {    // low, med-low, med-high, high order bits
    for (j <- 0 to 54) { // one for each LogicBlock on all orders
    val lut = Module(new LUT(inCount = 5,outCount = 1))
      lut.io.lut               := io.input((i + 1) * 32 - 1, i * 32) // take the high, med, or etc order bits
      lut.io.sel               := io.routing((i * 13 + j + 1) * 5 - 1, (i * 13 + j) * 5)
      io.outputs((i*55) + j)   := lut.io.res(0) // take logic block # j, and attach input i to it.
    }
  }
}


class RoutingTableTests(c: RoutingTable) extends Tester(c) {

}

object RoutingTableTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
      () => Module(new RoutingTable()))
    {
      c => new RoutingTableTests(c)
    }
  }
}
