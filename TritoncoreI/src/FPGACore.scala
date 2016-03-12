import Chisel._
import scala.collection.mutable.ArrayBuffer

class FPGACore(inputPins : Int, outputPins : Int, dClk : Clock) extends Module {
  val numberOfLuts = 256 - inputPins; /* the number of lookup tables is 256, but rounded down */
                                      /* to get good numbers in the routing table.            */
  
  val routingSelectBitsPerLutIn = 6; /* log_2(256/4). the 4 comes from the 4 inputs to LUTS.  */
                                     /* to achieve full connectivity, we only need to route   */
                                     /* each of the 4 inputs to a different 1/4th of the      */
                                     /* outputs of all LUTs.                                  */
  // how to program:
  // 1. send ROUTING of input (0) to LUTs 0-15 (the luts whose output is connected to out)
  // 2. send ROUTING of input (0) to the input of LUTs 16-239 (addresses 240-354 are the pin inputs)
  //
  //   repeat 1. and 2. for inputs (1),(2),(3).      (0) is LSB, (3) is MSB for select values
  //
  //   when programming input (3), you have your chance to select from the input.
  //     the addressees for pin inputs are 48-63, on input 3 for each LUT
  // 3. send FFen, then reVal, then lookup vals LSBF for LUTS 0-15 (output LUTS)
  // 4. same for LUTs 16-239
  // the total bits required should be 6*960 + 18*240 = 10,080
  val io = new Bundle {
    val dta   = Bits(INPUT,  1)
    val den   = Bits(INPUT,  1)
    val pin   = Bits(INPUT,  inputPins)  // the input pins
    val reset = Bits(INPUT,  1)
    val pot   = Bits(OUTPUT, outputPins) // the output pins
  }
  io.pot      := UInt(0)  // wird CHISEL req
  // 960 outputs comes from 240 luts * 4 inputs each
  val grt      = Module(new GeneralRoutingTable(dClk, 256, 960, 4)) // General Routing Table
  grt.io.in   := UInt(0)  // once again, that weird CHISEL req
  val firstLut = Module(new ShiftRegisteredLogicCell4(dClk))        // the first LUT
  val luts     = new ArrayBuffer[ShiftRegisteredLogicCell4]()       // the other 239 LUTs
  luts        += firstLut
  
  io.pot(0)          := firstLut.io.res // program the LUTs that are connected to ouput
  firstLut.io.den    := io.den
  //firstLut.io.dta  := (ignored until later)
  firstLut.io.sel    := UInt(0) // the weird CHISEL req
  // the following are chosen so that we follow the rule of sending low-order selections
  // to the GRT first
  firstLut.io.sel(0) := grt.io.out(0)   // the low-order bit goes to grouping 3
  firstLut.io.sel(1) := grt.io.out(240) // the next lowest bit goes to grouping 2
  firstLut.io.sel(2) := grt.io.out(480) // the high-ish bit goes to grouping 1
  firstLut.io.sel(3) := grt.io.out(720) // the high-order bit goes to grouping 0
  firstLut.io.reset  := io.reset        // pass along the reset
  grt.io.in(0)       := firstLut.io.res // the lut which you send your data to first has output that routs to grouping (0)
  grt.io.dta         := firstLut.io.cot // data carry out of the first LUT goes to the GRT
  grt.io.den         := io.den          // set the grt data enable

  for (i <- 1 to 15) { // notice by the direction of the carries that we are creating LUTs
                       //  in reverse order from the flow of data.
    val lut     = Module(new ShiftRegisteredLogicCell4(dClk))
    luts       += lut
    
    io.pot(i)        := lut.io.res // program the LUTs that are connected to ouput
    lut.io.den       := io.den
    luts(i-1).io.dta := lut.io.cot
    lut.io.sel       := UInt(0) // the weird CHISEL req
    // the following are chosen so that we follow the rule of sending low-order selections
    // to the GRT first
    lut.io.sel(0)    := grt.io.out(0   + i) // the low-order bit goes to grouping 3
    lut.io.sel(1)    := grt.io.out(240 + i) // the next lowest bit goes to grouping 2
    lut.io.sel(2)    := grt.io.out(480 + i) // the high-ish bit goes to grouping 1
    lut.io.sel(3)    := grt.io.out(720 + i) // the high-order bit goes to grouping 0
    lut.io.reset     := io.reset            // pass along the reset
    grt.io.in(i)     := lut.io.res          // the lut which you send your data to first has output that routs to grouping (0)
  }
  
  for (i <- 16 to 239) {
    
    val lut     = Module(new ShiftRegisteredLogicCell4(dClk))
    luts       += lut
    
    //io.pot(i)        := lut.io.res  (this line is the only difference from the loop above)
    lut.io.den       := io.den
    luts(i-1).io.dta := lut.io.cot
    lut.io.sel       := UInt(0) // the weird CHISEL req
    // the following are chosen so that we follow the rule of sending low-order selections
    // to the GRT first
    lut.io.sel(0)    := grt.io.out(0   + i) // the low-order bit goes to grouping 3
    lut.io.sel(1)    := grt.io.out(240 + i) // the next lowest bit goes to grouping 2
    lut.io.sel(2)    := grt.io.out(480 + i) // the high-ish bit goes to grouping 1
    lut.io.sel(3)    := grt.io.out(720 + i) // the high-order bit goes to grouping 0
    lut.io.reset     := io.reset            // pass along the reset
    grt.io.in(i)     := lut.io.res          // the lut which you send your data to first has output that routs to grouping (0)
  }
  
  luts(239).io.dta := io.dta    // data flow in on the last LUT we created
  
  // last thing we do is hook up the inputs to the GRT
  for (i <- 0 to 15)
    grt.io.in(240 + i) := io.pin(i)
  
}

class FPGACoreTests(c: FPGACore) extends Tester(c) {
  // ======== START TEST 1 =========
  
  // lets connect output 1 to itself and enable its flipflop to try and reverse it each time.
  // output 1 is available on input 0.
  // first, lets program the GRT. We are programming output 0, and selecting input 0. that means a bunch of 0's
  
  // actually, we can just not do that because everything defaults to 0's.
  // next step is to program the LUT. this is the firs LUT that we send data. in fact, lets just program all the LUTs the same.
  poke(c.io.den, 1)
  
  for (i <- 0 to 239) {
    poke(c.io.dta, 1)   // FFen (true)
    step(1)
    poke(c.io.dta, 1)   // FFreset (1)
    step(1)
    for (j <- 0 to 7) { // lower will always be a 1. upper will always be a 0. this makes stuff switch.
      poke(c.io.dta, 1) // the lower bit of two is 1
      step(1)
      poke(c.io.dta, 0) // the upper bit of two is 0
      step(1)
    }
  }
  
  poke(c.io.den, 0) // done programming. lets see what happened.
  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)
  
  expect(c.io.pot, 0xFFFF)
  step(1)
  expect(c.io.pot, 0)
  step(1)
  expect(c.io.pot, 0xFFFF)
  step(1)
  expect(c.io.pot, 0)
  step(1)
  expect(c.io.pot, 0xFFFF)
  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)
  expect(c.io.pot, 0xFFFF)
  poke(c.io.reset, 1)
  step(1)
  
  expect(c.io.pot, 0xFFFF)
  poke(c.io.reset, 0)
  expect(c.io.pot, 0xFFFF)
  
  // ======== END TEST 1 =========
  
  
  //printf("About to start test 2. Press enter to contnue.");
  //var a = scala.io.StdIn.readLine()
  
  // ======== START TEST 2 =========
  
  // test: connect input directly to output.
  poke(c.io.den, 1)  // set data enable
  poke(c.io.dta, 0)
  
  var inputAddress = 50;
  for (i <- 1 to (720)) { // first lets route all the inputs we dont need to the output of LUTs 0, 63, etc...
    for (j <- 0 to 5) {
      step(1)
    }
  }
  // 720*6 = 4320 bits so far, all going to routing
  inputAddress = 48;
  for (i <- 0 to 15) {  // route input pins to sel(3) on the output pins
    for (j <- 0 to 5) {
      poke(c.io.dta, ((inputAddress >> j) & 1))
      step(1)
    }
    inputAddress += 1
  }
  
  // 6*16 bits + 4320 = 4416 bits
  poke(c.io.dta, 0)
  for (i <- 16 to 239) { // route everything else we dont need with 0's
    for (j <- 0 to 5) {
      step(1)
    }
  }
  // 224*6 = 1344 + 4416 = 5760 bits
  
  // 240*4*6 = 5760 bits so far (routing bits are done, now for LUT programming)
  for (i <- 0 to 15) {  // program the output LUTs
    poke(c.io.dta, 0)   // FFen (false)
    step(1)
    poke(c.io.dta, 0)   // FFreset (dont care)
    step(1)
    poke(c.io.dta, 0)   // low-order is 0's
    for (j <- 0 to 7) {
      step(1)
    }
    poke(c.io.dta, 1)   // high-order bits are 1's
    for (j <- 0 to 7) {
      step(1)
    }
  }
  
  // we're on bit number 5760 + 16*18 = 6048
  poke(c.io.dta, 0) // program the rest of the cells with 0's
  for (i <- 1 to (240-16)) {
    poke(c.io.dta, 0)   // FFen (true), just cuz
    step(1)
    poke(c.io.dta, 0)   // FFreset (0)
    poke(c.io.dta, 0)   // FFreset (0)
    step(1)
    for (j <- 0 to 15) { // low-order is 1's
      step(1)
    }
  }
  // 224*18 = 4032 + 6048 = 10,080
  
  poke(c.io.den, 0) // done programming. lets see what happened.
  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)
  
  poke(c.io.pin, 0xFFFF)
  update   // note that this is NOT a cycle.
  update   // two updates because we have some issues with CHISEL. likely due to the combinational logic loops.
  expect(c.io.pot, 0xFFFF)
  
  poke(c.io.pin, 0xFF00)
  update
  update
  expect(c.io.pot, 0xFF00)
  
  poke(c.io.pin, 0xF0FF)
  update
  update
  expect(c.io.pot, 0xF0FF)
  
  poke(c.io.pin, 0xAABB)
  update
  update
  expect(c.io.pot, 0xAABB)
  
  poke(c.io.pin, 0xABCD)
  update
  update
  expect(c.io.pot, 0xABCD)
  
  // ======== END TEST 2 =========
}

object coreTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness", "--noCombLoop"),
       () => Module(new FPGACore(16, 16, Driver.implicitClock))){c => new FPGACoreTests(c)}
  }
}
