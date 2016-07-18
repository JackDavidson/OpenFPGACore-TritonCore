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
    val reset       = Bits(INPUT,     1) // resets all flipflops on LocgicBlock ouptputs
    val outputs     = Bits(OUTPUT,   32)
  }
  io.outputs := UInt(0) // weird CHISEL req

  val programming = new ArrayBuffer[UInt]()
  val programmingInOne = new UInt()
  programmingInOne := UInt(0, width = 485 * 32)

  for (i <- 1 to 486) { // (1100  +  992) *  5 = 10460
                        // (960   +  866) *  2 = 3652
                        // (10460 + 3652 + 1440) / 32 = 486
                        //

    val regForProgramming = Reg(init = UInt(0, width = 32))
    programming += regForProgramming
    //io.out(i-1) := registers(i-1) // connect the just-creaed Reg to the output
    programmingInOne(i*32 + 31, i*32) := regForProgramming(31,0)
  }

  when (io.dataenable(0)) {
    programming(0) := io.programming
    for (i <- 1 to 485) { /* soooo much easier than verilog */
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
    multBlock.io.reset       := io.reset
    multBlock.io.registerAll := io.dataenable
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
    addBlock.io.reset       := io.reset
    addBlock.io.registerAll := io.dataenable
    val offset               = bitsUsedByMultBlock + (i * totalSize)
    addBlock.io.routing     := programmingInOne(offset + routingSize - 1, offset)
    addBlock.io.programming := programmingInOne(offset + totalSize - 1, offset + routingSize)
  }
  val bitsUsedByAddBlock = (1100 + 992) * addBlockCount
  val totalRoutingBitsUsed = bitsUsedByMultBlock + bitsUsedByAddBlock

  // 32 global inputs, plus 7*32 outputs from sub-blocks = 256 inputs. there are 64*7+32=480 outputs from the tale.
  // each input needs to select from one of 8 values, so 256/4=32 groupings.
  // that gives us 3 bits for routing each output, and 480 outputs = 1440 routing bits
  val routingTable = Module(new RoutingTableB(256, 480, 32))
  routingTable.io.routing := programmingInOne(totalRoutingBitsUsed + 1440 - 1, totalRoutingBitsUsed)

  // now that we have finished setting up the programming, its time to connect the inputs of the routing table
  val inputsPerBlock = 32
  routingTable.io.input := UInt(0) // weird CHISEL req

  for ((addBlock, i) <- addBlocks.zipWithIndex) {
    routingTable.io.input((i+1)*inputsPerBlock-1,i*inputsPerBlock) := addBlock.io.outputs
  }

  val routeInputsUsedSoFar = addBlocks.length * inputsPerBlock

  for ((multBlock, i) <- multBlocks.zipWithIndex) {
    val offset = routeInputsUsedSoFar
    routingTable.io.input((i+1)*inputsPerBlock-1 + offset, i*inputsPerBlock + offset) := multBlock.io.outputs
  }
  val routeInputsUsedSoFar2 = routeInputsUsedSoFar + multBlocks.length * inputsPerBlock
  routingTable.io.input(routeInputsUsedSoFar2 + inputsPerBlock - 1, routeInputsUsedSoFar2) := io.input


  // now we are done connectig the inputs of the routing table. time to connect the outputs of the routing table.
  val outputsPerBlock = 32

  for ((addBlock, i) <- addBlocks.zipWithIndex) {
    addBlock.io.input := routingTable.io.outputs((i+1)*outputsPerBlock-1,i*outputsPerBlock)
  }

  val routeOutputsUsedSoFar = addBlocks.length * outputsPerBlock

  for ((multBlock, i) <- multBlocks.zipWithIndex) {
    val offset = routeOutputsUsedSoFar
    multBlock.io.input := routingTable.io.outputs((i+1)*outputsPerBlock-1 + offset, i*outputsPerBlock + offset)
  }
  val routeOutputsUsedSoFar2 = routeInputsUsedSoFar + multBlocks.length * inputsPerBlock
  io.outputs := routingTable.io.outputs(routeOutputsUsedSoFar2 + outputsPerBlock - 1, routeOutputsUsedSoFar2)

  // now that everything is connected and all the programming is set up, we're done.
}



class TritoncoreIITests(c: TritoncoreII) extends Tester(c) {
  def sendDataToTritoncore(value : ModifyableBigInt): Unit = {
    poke(c.io.dataenable, 1)
    for (i <- 0 to 485) {
      poke(c.io.programming, value.get32(i))
      step(1)
    }
    poke(c.io.dataenable, 0)
  }
  // === first test, send all 0's ====
  val programming = new ModifyableBigInt()
  if (false) {
    sendDataToTritoncore(programming)
    expect(c.io.outputs, 0)
    poke(c.io.input, 0x12345678)
    expect(c.io.outputs, 0x12345678)
  }

  // === second test, route inputs straight to outputs ====
  if (true) {
    sendDataToTritoncore(programming)
    expect(c.io.outputs, 0)
    poke(c.io.input, 0x12345678)
    update
    update
    update
    update
    update
    update
    update
    update
    update
    update
    update
    expect(c.io.outputs, 0x12345678)
  }

  // === third test, send all 1's ====
  if (false) {
    programming.value = BigInt(-1)
    sendDataToTritoncore(programming)
    poke(c.io.input, 0x12345678)
    step(1)
    update
    update
    update
    update
    update
    update
    update
    update
    update
    expect(c.io.outputs, 0xFFFFFFFF)
  }

  // === third test, flip input bits ====
  /*programming.value = BigInt(0)

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


  for (i <- 0 to 31) { // take the top-level routing table and connect the first 32 inputs to the last 32 outputs
    // adders, mults,
    val offset = 10460 + 3652 +

  }

  val input = new ModifyableBigInt()
  input.setBits(31, 16, 0xABCD)
  input.setBits(15,  0, 0x1234)
  poke(c.io.input, input.value)
  update
  update
  update
  expect(c.io.outputs, input.value)*/
}

object TritoncoreIITestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", /*"--compile",*/ "--test", /*"--genHarness",*/ "--noCombLoop"),
      () => Module(new TritoncoreII()))
    {
      c => new TritoncoreIITests(c)
    }
  }
}