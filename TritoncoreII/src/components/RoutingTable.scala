import java.math.BigInteger

import Chisel._

class RoutingTable(inputCount : Int = 128, outputCount : Int = 220, groupings : Int = 4) extends Module {
  val routingBitsPerOutput = (scala.math.log(inputCount/groupings)/scala.math.log(2)).toInt
  val numberOfRoutingBits = outputCount * routingBitsPerOutput
  val inputGroupingSize = inputCount / groupings
  val io = new Bundle {
    val input      = Bits(INPUT,  inputCount)
    val routing    = Bits(INPUT,  numberOfRoutingBits)
    val outputs    = Bits(OUTPUT, outputCount)
  }

  io.outputs := UInt(0) // weird CHISEL req


  val groupingSize = outputCount / groupings
  for (i <- 0 to (groupings-1)) {    // low, med-low, med-high, high order bits
    for (j <- 0 to (groupingSize - 1)) { // one for each LogicBlock on all orders
    val lut = Module(new LUT(inCount = routingBitsPerOutput,outCount = 1))
      lut.io.lut       := io.input((i + 1) * inputGroupingSize - 1, i * inputGroupingSize) // take the high, med, or etc order bits
      lut.io.sel       := io.routing((i * groupingSize + j + 1) * routingBitsPerOutput - 1, (i * groupingSize + j) * routingBitsPerOutput)
      io.outputs((i*groupingSize) + j)   := lut.io.res(0) // take logic block # j, and attach input i to it.
    }
  }
}


class RoutingTableTests(c: RoutingTable) extends Tester(c) {
  val routing = new ModifyableBigInt()
  // we only need to route each of the 32 inputs we care about to their respective LogicBlocks.
  // an easy way to do this is to simply take ech of the first two routing domains and route input 1 to output 1,
  // input 2 to output 2, etc.

  for (i <- 0 to 31) {
    // 5 bits per input.
    routing.setBits(5 * i + 4, 5 * i, i)
  }

  for (i <-0 to 31) {
    routing.setBits(5 * i + 4 + (5 * 55), 5 * i + (5 * 55), i)
  }

  println("routing value is: " + routing)

  poke(c.io.routing, routing.value)
  poke(c.io.input, new BigInteger("FF008000F0C02", 16))
  expect(c.io.outputs, new BigInteger("7F804000000000F0C02", 16))

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
