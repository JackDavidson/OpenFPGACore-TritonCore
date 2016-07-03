import Chisel._


/**
  * the WrapperAdder is simply a wrapper around an addition unit.
  *   routed inputs:  17   (carryIn, inputA, inputB)
  *   routed outputs:  9   (result, carryOut)
  *   programming:     0
  *   other inputs:    0
  */
class WrapperAdder extends Module {
  val io = new Bundle {
    val carryIn     = Bits(INPUT,  1)
    val inputA      = Bits(INPUT,  8) // the first input number
    val inputB      = Bits(INPUT,  8) // the second input number
    val result      = Bits(OUTPUT, 8) // the addition result
    val carryOut    = Bits(OUTPUT, 1)
  }

  // the following line is weird. We need a carry-in and a carry-out. Hopefully verilog will optomize this.
  val additionResult = UInt(0, width = 9) + io.inputA + io.inputB + io.carryIn
  io.result := additionResult(7,0);
  io.carryOut := additionResult(8);
}

class WrapperAdderTests(c: WrapperAdder) extends Tester(c) {
  poke(c.io.inputA, 0xAA)

  var valuesWithResults = Array(Array(0,0,0xAA,0), Array(1,0,0xAB,0), Array(0,2,0xAC,0), Array(1,3,0xAE,0),
    Array(0,0x90,0x3A,1))

  for (valueWithResult <- valuesWithResults) {
    poke(c.io.carryIn, valueWithResult(0))
    poke(c.io.inputB, valueWithResult(1))
    expect(c.io.result, valueWithResult(2))
    expect(c.io.carryOut, valueWithResult(3))
  }
}

object WrapperAdderTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
      () => Module(new WrapperAdder()))
    {
      c => new WrapperAdderTests(c)
    }
  }
}