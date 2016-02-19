import Chisel._

class FPGACore(inputPins : Int, outputPins : Int, dClk : Clock) extends Module {
  val numberOfLuts = 256 - inputPins; /* the number of lookup tables is 256, but rounded down */
                                      /* to get good numbers in the routing table.            */
  
  val routingSelectBitsPerLutIn = 6; /* log_2(256/4). the 4 comes from the 4 inputs to LUTS.  */
                                     /* to achieve full connectivity, we only need to route   */
                                     /* each of the 4 inputs to a different 1/4th of the      */
                                     /* outputs of all LUTs.                                  */

  val io = new Bundle {}             
  printf("Hello World!\n")
}

class FPGACoreTests(c: FPGACore) extends Tester(c) {
  step(1)
}

object coreTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
       () => Module(new FPGACore(16, 16, Driver.implicitClock))){c => new FPGACoreTests(c)}
  }
}
