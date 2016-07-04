import Chisel._
import scala.collection.mutable.ArrayBuffer


/**
  * the AdderBlock is a block that includes one WrapperAdder and a set of LogicBlocks. The number of LogicBlocks is
  * determined by the routing. We take our 64 inputs and add them to whatever internal values we also want to route.
  * A good number to go for is 128. If we have 128 inputs to the routing table, that leaves us with 128/4=32 inputs per
  * LogicBlock input that we need to route in order to achive full connectivity between all components within the
  * AdderBlock. Note that fulll connectivity is not necessary, but makes it simpler to generate a bitstream for the
  * FPGA.
  *
  * In any case, the 128 inputs leaves us with space for exactly 55 LogicBlocks. This is because 9 of the 128 inputs
  * are used by the WrapperAdder's output. 128-64-9 = 55
  *
  * log_2(32) = 5, so we need 5 bits to route every output of the routing table. We have 55 LC's, which means 220
  * inputs to route to. 220 * 5 = 1100 routing bits.
  *
  * we have 55 LogicBlocks, each of which requires 18 bits to program. That leaves us with 55*18 = 990 programming
  * bits.
  *
  *
  *
  * routing:
  *   There are 128 inputs to the routing table route. They are numbered as follows:
  *   ( 15,  0)       io.inputs( 15,  0)
  *   ( 17, 16) adder.io.result(  1,  0)
  *   ( 18    )     logicBlocks( 52    )
  *   ( 31, 19)     logicBlocks( 12,  0)
  *   ( 47, 32)       io.inputs( 31, 16)
  *   ( 49, 48) adder.io.result(  3,  2)
  *   ( 50    )     logicBlocks( 53    )
  *   ( 63, 51)     logicBlocks( 25, 13)
  *   ( 79, 64)       io.inputs( 47, 32)
  *   ( 81, 80) adder.io.result(  5,  4)
  *   ( 82    )     logicBlocks( 54    )
  *   ( 95, 83)     logicBlocks( 38, 26)
  *   (111, 96)       io.inputs( 63, 48)
  *   (113,112) adder.io.result(  7,  6)
  *   (114    ) adder.io.carryOut
  *   (127,115)     logicBlocks( 51, 39)
  *
  *
  *  There are 220 outputs from the routing table. Each output routes to an input on a logic block:
  *    0-54    : LSB's on all LUTs in logic blocks
  *    55-109  : med-low bits on LUT inputs
  *    110-164 : med-high bits on LUT inputs
  *    165-219 : MSB's on all LUT inputs
  *   note that this system is the same as the TritoncoreI
  *
  *
  *  There are 32 outputs from this module. they are numbered as follows:
  *   io.outputs( 7,0) := adder.io.result
  *   io.outputs( 8  ) := adder.io.carryOut
  *   io.outputs(31,9) := logicBlocks(54,32)
  *
  *
  *   routed inputs:    64   (inputs)
  *   routed outputs:   32   (outputs)
  *   programming:    2090   (routing, programming)
  *   other inputs:      1   (registerAll)
  */
class AdderBlock extends Module {
  val io = new Bundle {
    val input      = Bits(INPUT,    64)
    val routing     = Bits(INPUT,  1100)
    val programming = Bits(INPUT,   990)
    val registerAll = Bits(INPUT,     1) // this is used when we are programming the FPGA. We register on all flipflops
    val outputs     = Bits(OUTPUT,   32)
  }

  val adder   = Module(new WrapperAdder)

  val logicBlocks = new ArrayBuffer[LogicBlock]()

  for (i <- 0 to 54) {
    val logicBlock = Module(new LogicBlock())
    logicBlocks += logicBlock
    logicBlock.io.programmedInputs := io.programming(i*18 + 15,i*18)
    logicBlock.io.enableFlipflop   := io.programming(i*18 + 16)
    logicBlock.io.flipflopReset    := io.programming(i*18 + 17)
  }

  val routingTable = Module(new RoutingTable())
  routingTable.io.input := UInt(0) // weird CHISEL req

  for (i <- 0 to 3) { // the 64 inputs go to the first 16 inputs for each domain.
    for (j <- 0 to 15) {
      routingTable.io.input(i*32 + j) := io.input((i*16) + j)
    }
  }

  for (i <- 0 to 3) { // the 8 adder outputs (we exclude carry) go across the domains
    for (j <- 0 to 1) {
      routingTable.io.input(i * 32 + 16 + j) := adder.io.result((i * 2) + j)
    }
  }
  // the carry will go on the next available high-order routing table input.
  routingTable.io.input(114) := adder.io.carryOut

  // the first three LogicBlocks will go to the lower-order routing table inputs.
  routingTable.io.input(18)  := logicBlocks(52).io.result
  routingTable.io.input(50)  := logicBlocks(53).io.result
  routingTable.io.input(82)  := logicBlocks(54).io.result

  // the rest of the routing table inputs are the rest of the logicBlock outputs
  for (i <- 0 to 3) {
    for (j <- 0 to 12) {
      routingTable.io.input(i * 32 + 19 + j) := logicBlocks(i * 13 + j).io.result
    }
  }

  for (logicBlock <- logicBlocks) {
    logicBlock.io.selectInputs := UInt(0) // weird CHISEL req
  }
  for (i <- 0 to 3) {    // low, med-low, med-high, high order bits
    for (j <- 0 to 54) { // one for each LogicBlock on all orders
      logicBlocks(j).io.selectInputs(i) := routingTable.io.outputs(i * 55 + j) // take logic block # j, and attach input i to it.
    }
  }

  io.outputs := UInt(0) // weird CHISEL req
  // our low-order outputs are driven by the adder result
  io.outputs(7,0) := adder.io.result
  io.outputs(8)   := adder.io.carryOut
  // our high-order outputs are driven by our high-order LogicBlocks
  io.outputs := UInt(0) // weird CHISEL req
  for (i <- 0 to 22) {
    io.outputs(i + 9) := logicBlocks(i + 32).io.result
  }
}

class AdderBlockTests(c: AdderBlock) extends Tester(c) {

}

object AdderBlockTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness", "--noCombLoop"),
      () => Module(new AdderBlock()))
    {
      c => new AdderBlockTests(c)
    }
  }
}