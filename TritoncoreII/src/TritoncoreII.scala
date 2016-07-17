import Chisel._
import scala.collection.mutable.ArrayBuffer


/**
  * The TritoncoreII has 32 input wires, 32 output wires, a clock input, 32 wires for sending the bitstream, and a wire
  *   for enabling the bitstream.
  *
  *
  */
class TritoncoreII extends Module {
  val io = new Bundle {
    val input       = Bits(INPUT,    32)
    val programming = Bits(INPUT,    32)
    val dataenable  = Bits(INPUT,     1) // this is used when we are programming the FPGA. We register on all flipflops
    val outputs     = Bits(OUTPUT,   32)
  }
  io.outputs := UInt(0) // weird CHISEL req

  val programming = new ArrayBuffer[UInt]()
  val programmingInOne = new UInt()
  programmingInOne := UInt(0, width = 483 * 32)

  io.outputs    := UInt(0) // set a default value for io.outputs (weird CHISEL req)

  for (i <- 1 to 483) { // (1100  +  992) *  5 = 10460
                        // (960   +  866) *  2 = 3652
                        // (10460 + 3652 + 1344) / 32 = 483
                        //

    val regForProgramming = Reg(init = UInt(0, width = 32))
    programming += regForProgramming
    //io.out(i-1) := registers(i-1) // connect the just-creaed Reg to the output
    programmingInOne(i*32 + 31, i*32) := regForProgramming(31,0)
  }

  when (io.dataenable(0)) {
    programming(0) := io.programming
    for (i <- 1 to 482) { /* soooo much easier than verilog */
      programming(i) := programming(i-1)
    }
  }


  val multBlocks = new ArrayBuffer[MultiplyBlock]()
  val multBlockCount = 2
  for (i <- 0 to multBlockCount - 1) {
    val multBlock = Module(new MultiplyBlock())
    val routingSize = 960
    val programmingSize = 866
    val totalSize = routingSize + programmingSize
    multBlocks += multBlock
    multBlock.io.routing     := programmingInOne((i * totalSize) + routingSize - 1, (i * totalSize))
    multBlock.io.programming := programmingInOne((i * totalSize) + totalSize - 1, (i * totalSize) + routingSize)
  }

  val bitsUsedByMultBlock = (960 + 866) * multBlockCount

  val addBlocks = new ArrayBuffer[AdderBlock]()
  val addBlockCount = 5
  for (i <- 0 to addBlockCount - 1) {
    val addBlock = Module(new AdderBlock())
    val routingSize = 1100
    val programmingSize = 992
    val totalSize = routingSize + programmingSize
    addBlocks += addBlock
    val offset = bitsUsedByMultBlock + (i * totalSize)
    addBlock.io.routing := programmingInOne(offset + routingSize - 1, offset)
    addBlock.io.programming := programmingInOne(offset + totalSize - 1, offset + routingSize)
  }
  val bitsUsedByAddBlock = (1100 + 992) * addBlockCount
  val totalRoutingBitsUsed = bitsUsedByMultBlock + bitsUsedByAddBlock

  // 32 global inputs, plus 7*32 outputs from sub-blocks = 256 inputs. 64*7=448 outputs from the tale.
  // each input needs to select from one of 8 values, so 256/4=32 groupings.
  // that gives us 3 bits for routing each output, and 448 outputs = 1344 routing bits
  val routingTable = Module(new RoutingTable(256, 448, 32))
  routingTable.io.routing := programmingInOne(totalRoutingBitsUsed + 1343, totalRoutingBitsUsed)


}

class TritoncoreIITests(c: TritoncoreII) extends Tester(c) {

}

object TritoncoreIITestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness", "--noCombLoop"),
      () => Module(new TritoncoreII()))
    {
      c => new TritoncoreIITests(c)
    }
  }
}