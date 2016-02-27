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
  // 1. send routing of input (0) to the input of LUTs 0-15 (the luts whose output is connected to out)
  // 2. send routing of input (0) to LUTs 16-239 (addresses 240-354 are the pin inputs)
  //   repeat 1. and 2. for inputs (1),(2),(3).      (0) is LSB, (3) is MSB select values
  //    when programming input (3), you have your chance to select from the input.
  //     the addressees for pin inputs are 48-63, on input 3 for each LUT
  // 3. send FFen, then reVal, then lookup vals LSBF for LUTS 0-15 (output LUTS)
  // 4. same for LUTs 16-239
  // the total bits required should be 6*960 + 18*240 = 10,080
  val io = new Bundle {
    val dta   = Bits(INPUT,  1)
    val den   = Bits(INPUT,  1)
    val pin   = Bits(INPUT,  inputPins)  // the input pins
    val reset = Bits(INPUT, 1)
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
  firstLut.io.sel(0) := grt.io.out(720) // the low-order bit goes to grouping 3
  firstLut.io.sel(1) := grt.io.out(480) // the next lowest bit goes to grouping 2
  firstLut.io.sel(2) := grt.io.out(240) // the high-ish bit goes to grouping 1
  firstLut.io.sel(3) := grt.io.out(0)   // the high-order bit goes to grouping 0
  firstLut.io.reset  := io.reset        // pass along the reset
  grt.io.in(0)       := firstLut.io.res // the lut which you send your data to first has output that routs to grouping (0)
  grt.io.dta         := firstLut.io.cot // data carry out of the first LUT goes to the GRT

  for (i <- 1 to 15) { // notice by the direction of the carries that we are creating LUTs
                       //  in reverse order from the flow of data.
    val lut     = Module(new ShiftRegisteredLogicCell4(dClk))
    luts       += firstLut
    
    io.pot(i)        := lut.io.res // program the LUTs that are connected to ouput
    lut.io.den       := io.den
    luts(i-1).io.dta := lut.io.cot
    lut.io.sel       := UInt(0) // the weird CHISEL req
    // the following are chosen so that we follow the rule of sending low-order selections
    // to the GRT first
    lut.io.sel(0)    := grt.io.out(720 + i) // the low-order bit goes to grouping 3
    lut.io.sel(1)    := grt.io.out(480 + i) // the next lowest bit goes to grouping 2
    lut.io.sel(2)    := grt.io.out(240 + i) // the high-ish bit goes to grouping 1
    lut.io.sel(3)    := grt.io.out(0   + i) // the high-order bit goes to grouping 0
    lut.io.reset     := io.reset            // pass along the reset
    grt.io.in(i)     := lut.io.res          // the lut which you send your data to first has output that routs to grouping (0)
  }
  
  for (i <- 16 to 239) {
    
    val lut     = Module(new ShiftRegisteredLogicCell4(dClk))
    luts       += firstLut
    
    //io.pot(i)        := lut.io.res  (this line is the only difference from the loop above)
    lut.io.den       := io.den
    luts(i-1).io.dta := lut.io.cot
    lut.io.sel       := UInt(0) // the weird CHISEL req
    // the following are chosen so that we follow the rule of sending low-order selections
    // to the GRT first
    lut.io.sel(0)    := grt.io.out(720 + i) // the low-order bit goes to grouping 3
    lut.io.sel(1)    := grt.io.out(480 + i) // the next lowest bit goes to grouping 2
    lut.io.sel(2)    := grt.io.out(240 + i) // the high-ish bit goes to grouping 1
    lut.io.sel(3)    := grt.io.out(0   + i) // the high-order bit goes to grouping 0
    lut.io.reset     := io.reset            // pass along the reset
    grt.io.in(i)     := lut.io.res          // the lut which you send your data to first has output that routs to grouping (0)
  }
  
  // last thing we do is hook up the inputs to the GRT
  for (i <- 0 to 15)
    grt.io.in(240 + i) := io.pin(i)
  
}

class FPGACoreTests(c: FPGACore) extends Tester(c) {
  poke(c.io.den, 1)
  poke(c.io.dta, 1)
  step(1) // set FF to enabled
  step(1) // set FF reset val to 1
  for (i <- 1 to 8)
    step(1)    // set low-order select bits to 1
    
  poke(c.io.dta, 0)
  for (i <- 1 to 8)
    step(1)    // set high-order select bits to 0
    
  poke(c.io.den, 0)
  
  peek(c.io.pot)
  step(1)
  peek(c.io.pot)
  step(1)
  peek(c.io.pot)
  step(1)
  peek(c.io.pot)
  step(1)
  peek(c.io.pot)
  step(1)
}

object coreTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness", "--noCombLoop"),
       () => Module(new FPGACore(16, 16, Driver.implicitClock))){c => new FPGACoreTests(c)}
  }
}
