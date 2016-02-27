import Chisel._
import scala.collection.mutable.ArrayBuffer

class FPGACore(inputPins : Int, outputPins : Int, dClk : Clock) extends Module {
  val numberOfLuts = 256 - inputPins; /* the number of lookup tables is 256, but rounded down */
                                      /* to get good numbers in the routing table.            */
  
  val routingSelectBitsPerLutIn = 6; /* log_2(256/4). the 4 comes from the 4 inputs to LUTS.  */
                                     /* to achieve full connectivity, we only need to route   */
                                     /* each of the 4 inputs to a different 1/4th of the      */
                                     /* outputs of all LUTs.                                  */

  val io = new Bundle {
    val dta = Bits(INPUT,  1)
    val den = Bits(INPUT,  1)
    val pin = Bits(INPUT,  inputPins)  // the input pins
    val pot = Bits(OUTPUT, outputPins) // the output pins
  }
  val grt      = Module(new GeneralRoutingTable(dClk, 256, 960, 4)) // General Routing Table
  val firstLut = Module(new ShiftRegisteredLogicCell4(dClk))        // the first LUT
  val luts     = new ArrayBuffer[ShiftRegisteredLogicCell4]()       // the other 239 LUTs
  luts        += firstLut  
  firstLut.io.den := io.den
  firstLut.io.dta := io.dta
  grt.io.den      := io.den
  grt.io.dta      := firstLut.io.cot
  
  
  // this is just a test. hook up the first lut in a loop to itself.
  firstLut.io.sel    := UInt(0) // that weird CHISEL req again
  firstLut.io.sel(3) := grt.io.out(outputPins - 1) // the last output of the GRT 
                                                   // (can be programmed first in our limited test)
                                                   // connect FirstLut's output to its high-order input
  grt.io.in          := UInt(0) // weird CHISEL req
  grt.io.in(0)       := firstLut.io.res
  io.pot             := UInt(0) // the default otput, that weird CHISEL req
  io.pot(0)          := firstLut.io.res
  
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
