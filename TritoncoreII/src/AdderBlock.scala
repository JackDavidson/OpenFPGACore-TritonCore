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
  * There are 64 inputs to this module. They all go to the routing table on inputs: (re-stated below under routing)
  *   input( 15,  0)   routingInput( 15,  0)
  *   input( 31, 16)   routingInput( 47, 32)
  *   input( 47, 32)   routingInput( 79, 64)
  *   input( 63, 48)   routingInput(111, 96)
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
  *   Internally, routing table inputs can route to outputs as follows: (these are reffered to as routing 'domains')
  *   in( 31,  0)  out( 54,  0)
  *   in( 63, 32)  out(109, 55)
  *   in( 95, 64)  out(164,110)
  *   in(127, 96)  out(219,165)
  *
  *   There are 220 outputs from the routing table. Each output routes to an input on a logic block:
  *   0-54    : LSB's on all LUTs in logic blocks
  *   55-109  : med-low bits on LUT inputs
  *   110-164 : med-high bits on LUT inputs
  *   165-219 : MSB's on all LUT inputs
  *   note that this system is the same as the TritoncoreI
  *
  *
  * WrapperAdder:
  *   There are 17 inputs to the adder. They are chosen with a goal of using LogicBlocks that are not output, but also
  *      are the LogicBlocks which are highest numbered:
  *   logicBlocks( 31, 24)   adder.io.inputB
  *   logicBlocks( 23, 16)   adder.io.inputA
  *   logicBlocks( 15    )   adder.io.carryIn
  *
  *
  * There are 32 outputs from this module. they are numbered as follows:
  *   io.outputs( 7,0) := adder.io.result
  *   io.outputs( 8  ) := adder.io.carryOut
  *   io.outputs(31,9) := logicBlocks(54,32)
  *
  *
  * programming:
  *   There are 55 Logic Blocks to program. Each one requires 18 bits. On the programming input, the least significant
  *   bits go to the lowest numbered logic cells. Within each group of 18 bits, the first 16 bits are routing, the
  *   next bit enables the flopflop, and the highest bit says what value the flipflop will reset to.
  *   The LSB for the programming is also the LSB on the 'programming' input. In other words, the directionality of the
  *   bits match the directionality of the 'programming' input.
  *
  *
  *   routed inputs:    64   (inputs)
  *   routed outputs:   32   (outputs)
  *   programming:    2090   (routing, programming)
  *   other inputs:      1   (registerAll)
  */
class AdderBlock extends Module {
  val io = new Bundle {
    val input       = Bits(INPUT,    64)
    val routing     = Bits(INPUT,  1100)
    val programming = Bits(INPUT,   990)
    val registerAll = Bits(INPUT,     1) // this is used when we are programming the FPGA. We register on all flipflops
    val outputs     = Bits(OUTPUT,   32)
  }
  io.outputs := UInt(0) // weird CHISEL req

  val adder   = Module(new WrapperAdder)

  val logicBlocks = new ArrayBuffer[LogicBlock]()

  for (i <- 0 to 54) {
    val logicBlock = Module(new LogicBlock())
    logicBlocks += logicBlock
    logicBlock.io.programmedInputs := io.programming(i*18 + 15,i*18)
    // we need to turn on the flipflop while programming, so flipflop is enabled upon registerAll
    logicBlock.io.enableFlipflop   := io.programming(i*18 + 16) | io.registerAll
    logicBlock.io.flipflopReset    := io.programming(i*18 + 17)
  }

  val routingTable = Module(new RoutingTable())
  routingTable.io.routing := io.routing
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

  // adder input B is driven by logic blocks 24-31, the next highest-order logic cells whose output is not already
  // going to be connected to the global outputs.
  adder.io.inputB := UInt(0) // weird CHISEL req
  for (i <- 24 to 31) {
    adder.io.inputB(i - 24) := logicBlocks(i).io.result
  }
  adder.io.inputA := UInt(0) // weird CHISEL req
  // the adder input A is connected to the next set of logic cell outputs.
  for (i <- 16 to 23) {
    adder.io.inputA(i - 16) := logicBlocks(i).io.result
  }
  // the carry-in is driven by logic block 15
  adder.io.carryIn := logicBlocks(15).io.result

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
      logicBlocks(j).io.selectInputs(i) := routingTable.io.outputs(i * 55 + j) // take logic block # j, and attach
                                                                               // input i to it.
    }
  }

  io.outputs := UInt(0) // weird CHISEL req
  // our low-order outputs are driven by the adder result
  io.outputs(7,0) := adder.io.result
  io.outputs(8)   := adder.io.carryOut
  // our high-order outputs are driven by our high-order LogicBlocks
  for (i <- 0 to 22) {
    io.outputs(i + 9) := logicBlocks(i + 32).io.result
  }
}

class AdderBlockTests(c: AdderBlock) extends Tester(c) {
  // ==== first test, pass-through values except on adder carry-out ====
  val programming = new ModifyableBigInt()

  for (i <- 16 to 23) { // these are the logic cells connected to adder, and which have their LSB connected
                        // to the input which they want to duplicate. These go to adder.io.inputA
    programming.setBits(i * 18 + 17, i * 18 + 0, 0x0AAAA) // pass-through on the lowest order. other bits are ignored
  }
  for (i <- 32 to 38) { // these are the logic cells connected to output, and which have their LSB connected
                        // to the input which they want to duplicate.
    programming.setBits(i * 18 + 17, i * 18 + 0, 0x0AAAA) // pass-through on the lowest order. other bits are ignored
  }
  for (i <- 39 to 54) { // these are the logic cells connected to output, and which have their second bit connected
                        // to the input which they want to duplicate.
    programming.setBits(i * 18 + 17, i * 18 + 0, 0x0CCCC) // pass-through on the second-to-low bit
  }
  println("programming values is: " + programming)

  val routing = new ModifyableBigInt()
  // we only need to route each of the 32 inputs we care about to their respective LogicBlocks.
  // an easy way to do this is to simply take ech of the first two routing domains and route input 1 to output 1,
  // input 2 to output 2, etc.

  for (i <- 16 to 23) { // adder input A. LUTs 16 to 23
    routing.setBits(5 * i + 4, 5 * i, i - 16) // route LSB for luts that go to the adder's input A
    println("routing table input: " + i + " to input number: " + (i-16))
  }

  var j = 9
  for (i <- 32 to 38) { // LUTs 32 to 38, duplicating inputs 9 to 16
    routing.setBits(5 * i + 4, 5 * i, j) // route LSB for luts that will simply duplicate to the output
    j = j + 1
  }

  for (i <-0 to 15) {
    val secondFromLsb = 5 * 55 // we're on the second bit inputs, now.
    val offset = 39 * 5 // logic cells 39-54
    routing.setBits(5 * i + 4 + secondFromLsb + offset, 5 * i + secondFromLsb + offset, i)
  }

  println("routing value is: " + routing)

  poke(c.io.routing, routing.value)
  poke(c.io.programming, programming.value)


  val input = new ModifyableBigInt()
  input.setBits(31, 16, 0xABCD)
  input.setBits(15,  0, 0x1234)
  poke(c.io.input, input.value)
  update
  update
  update
  expect(c.io.outputs, input.value)

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