import Chisel._
import scala.collection.mutable.ArrayBuffer


/**
  * See AdderBlock for more info on the calculations below.
  *
  * The 128 inputs leaves us with space for exactly 48 LogicBlocks. This is because 16 of the 128 inputs
  * are used by the WrapperAdder's output. 128-64-16 = 48
  *
  * we need 5 bits to route every output of the routing table. We have 48 LC's, which means 192
  * inputs to route to. 192 * 5 = 960 routing bits.
  *
  * we have 48 LogicBlocks, each of which requires 18 bits to program. That leaves us with 48*18 + 2= 866 programming
  * bits. The plus two is for the enableAdder and enableReg on the adder.
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
  *   ( 19, 16)  mult.io.result(  3,  0)
  *   ( 31, 20)     logicBlocks( 11,  0)
  *   ( 47, 32)       io.inputs( 31, 16)
  *   ( 51, 48)  mult.io.result(  7,  4)
  *   ( 63, 52)     logicBlocks( 23, 12)
  *   ( 79, 64)       io.inputs( 47, 32)
  *   ( 83, 80)  mult.io.result( 11,  8)
  *   ( 95, 84)     logicBlocks( 35, 24)
  *   (111, 96)       io.inputs( 63, 48)
  *   (115,112)  mult.io.result( 15, 12)
  *   (127,116)     logicBlocks( 47, 36)
  *
  *   Internally, routing table inputs can route to outputs as follows: (these are reffered to as routing 'domains')
  *   in( 31,  0)  out( 47,  0)
  *   in( 63, 32)  out( 95, 48)
  *   in( 95, 64)  out(143, 96)
  *   in(127, 96)  out(191,144)
  *
  *   There are 192 outputs from the routing table. Each output routes to an input on a logic block:
  *   0-47    : LSB's on all LUTs in logic blocks
  *   48-95   : med-low bits on LUT inputs
  *   96-143  : med-high bits on LUT inputs
  *   144-191 : MSB's on all LUT inputs
  *   note that this system is the same as the TritoncoreI
  *
  *
  * WrapperAdder:
  *   There are 17 inputs to the adder. They are chosen with a goal of using LogicBlocks that are not output, but also
  *      are the LogicBlocks which are highest numbered:
  *
  *   logicBlocks( 31, 24)   mult.io.inputB
  *   logicBlocks( 23, 16)   mult.io.inputA
  *
  *
  * There are 32 outputs from this module. they are numbered as follows:
  *   io.outputs( 7,0) := adder.io.result
  *   io.outputs( 8  ) := adder.io.carryOut
  *   io.outputs(31,9) := logicBlocks(54,32)
  *
  *
  *   routed inputs:    64   (inputs)
  *   routed outputs:   32   (outputs)
  *   programming:    1826   (routing, programming)
  *   other inputs:      1   (registerAll)
  */
class MultiplyBlock extends Module {
  val io = new Bundle {
    val input       = Bits(INPUT,    64)
    val routing     = Bits(INPUT,   960)
    val programming = Bits(INPUT,   866)
    val registerAll = Bits(INPUT,     1) // this is used when we are programming the FPGA. We register on all flipflops
    val outputs     = Bits(OUTPUT,   32)
  }
  io.outputs := UInt(0) // weird CHISEL req

  val mult   = Module(new WrapperMultiplier) // create the multiplication unit we will use
  mult.io.enableMult  := io.routing(864)     // enable/pass-through on the multiply unit
  mult.io.enableReg   := io.routing(865) | io.registerAll // enable the reg when we are programming, or if its turned on

  val logicBlocks = new ArrayBuffer[LogicBlock]()

  for (i <- 0 to 47) { // create all 47 logic blocks
    val logicBlock = Module(new LogicBlock())
    logicBlocks += logicBlock
    logicBlock.io.programmedInputs := io.programming(i*18 + 15,i*18) // connect them to the LSBs of programming
    // we need to turn on the flipflop while programming, so flipflop is enabled upon registerAll
    logicBlock.io.enableFlipflop   := io.programming(i*18 + 16) | io.registerAll
    logicBlock.io.flipflopReset    := io.programming(i*18 + 17)
  }

  val routingTable = Module(new RoutingTable(128, 192))
  routingTable.io.routing := io.routing
  routingTable.io.input := UInt(0) // weird CHISEL req

  for (i <- 0 to 3) { // the 64 inputs go to the first 16 inputs for each routing domain. takes inputs 0-15 per domain
    for (j <- 0 to 15) {
      routingTable.io.input(i*32 + j) := io.input((i*16) + j)
    }
  }

  for (i <- 0 to 3) { // the 16 mult outputs go across the domains. takes inputs 16-19 on each domain.
    for (j <- 0 to 3) {
      routingTable.io.input(i * 32 + 16 + j) := mult.io.result((i * 4) + j)
    }
  }

  // the rest of the routing table inputs are the rest of the logicBlock outputs. Takes inputs 20-31 on each domain.
  for (i <- 0 to 3) {
    for (j <- 0 to 11) {
      routingTable.io.input(i * 32 + 20 + j) := logicBlocks(i * 12 + j).io.result
    }
  }

  for (logicBlock <- logicBlocks) {       // initialize the inputs on each LogicBlock's select bits
    logicBlock.io.selectInputs := UInt(0) // weird CHISEL req
  }
  for (i <- 0 to 3) {    // low, med-low, med-high, high order bits
    for (j <- 0 to 47) { // one for each LogicBlock on all orders
      logicBlocks(j).io.selectInputs(i) := routingTable.io.outputs(i * 48 + j) // take logic block # j, and attach
                                                                               // input i to it.
    }
  }

  // our low-order outputs are driven by the adder result (0-15)
  io.outputs(15,0) := mult.io.result
  // our high-order outputs are driven by our high-order LogicBlocks (16-31)
  for (i <- 0 to 15) {
    io.outputs(i + 16) := logicBlocks(i + 32).io.result // i + 32 because we have 48 LBs and we want the top 16
  }

  // mult input B comes from the next available high-rder LBs.
  mult.io.inputB := UInt(0) // weird CHISEL req
  for (i <- 24 to 31) {
    mult.io.inputB(i - 24) := logicBlocks(i).io.result // logic blocks 24-31
  }
  mult.io.inputA := UInt(0) // weird CHISEL req
  // mult input A, same thing.
  for (i <- 16 to 23) {
    mult.io.inputA(i - 16) := logicBlocks(i).io.result // logic blocks 16 to 23
  }
}

class MultiplyBlockTests(c: MultiplyBlock) extends Tester(c) {
  // ==== first test, pass-through values except on adder carry-out. Adder is enabled ====
  val programming = new ModifyableBigInt()

  for (i <- 16 to 31) { // these are the logic cells whose output is connected to mult, and which have their LSB connected
    // to the input which they want to duplicate. These go to adder.io.inputA
    programming.setBits(i * 18 + 17, i * 18 + 0, 0x0AAAA) // pass-through on the lowest order. other bits are ignored
  }
  for (i <- 32 to 47) { // these are the logic cells connected to output, and which have their second bit connected
    // to the input which they want to duplicate.
    programming.setBits(i * 18 + 17, i * 18 + 0, 0x0CCCC) // pass-through on the second-to-low bit
  }
  println("programming values is: " + programming)

  val routing = new ModifyableBigInt()
  // we only need to route each of the 32 inputs we care about to their respective LogicBlocks.
  // an easy way to do this is to simply take ech of the first two routing domains and route input 1 to output 1,
  // input 2 to output 2, etc.

  for (i <- 16 to 31) { // route the low-order bits to the output LUTs they need to go to
    routing.setBits(5 * i + 4, 5 * i, i - 16) // route LSB for luts that go to the adder's input A
  }

  for (i <-0 to 15) {
    val secondFromLsb = 5 * 48 // we're on the second bit inputs, now.
    val offset = 32 * 5 // logic cells 32-47
    routing.setBits(5 * i + 4 + secondFromLsb + offset, 5 * i + secondFromLsb + offset, i)
  }

  println("routing value is: " + routing)

  poke(c.io.routing, routing.value)
  poke(c.io.programming, programming.value) // note that the multiply unit is disabled.


  val input = new ModifyableBigInt()
  input.setBits(31, 16, 0xABCD)
  input.setBits(15,  0, 0x1234)
  poke(c.io.input, input.value)
  update
  update
  update
  expect(c.io.outputs, input.value)
}

object MultiplyBlockTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness", "--noCombLoop"),
      () => Module(new MultiplyBlock()))
    {
      c => new MultiplyBlockTests(c)
    }
  }
}