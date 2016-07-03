import Chisel._

/**
  * the LogicBlock is a wrapper around a lookup table.
  *   routed inputs:   4   (selectInputs)
  *   routed outputs:  1   (result)
  *   programming:    18   (programmedInputs, enableFlipflop, flipflopReset)
  *   other inputs:    1   (reset)
  */
class LogicBlock extends Module {
  val io = new Bundle {
    val programmedInputs = Bits(INPUT,  16) // This data is provided by the GlobalProgrammer
    val enableFlipflop   = Bits(INPUT,  1 ) // Enables or disables the flipflop
    val flipflopReset    = Bits(INPUT,  1 ) // The value which the flipflop gets reset to
    val selectInputs     = Bits(INPUT,  4 ) // the 4 select inputs for the LUT
    val reset            = Bits(INPUT,  1 ) // the reset signal for the reg
    val result           = Bits(OUTPUT, 1 ) // the 1-bit output
  }

  val lookupTable = Module(new LUT())
  lookupTable.io.lut := io.programmedInputs  // the first 16 inputs are the values we select from
  lookupTable.io.sel := io.selectInputs      // the input to the

  val optionalReg = Reg(init = UInt(0, width = 1)) // reg on the end of the lookup table
  optionalReg := Mux(io.reset(0), io.flipflopReset, lookupTable.io.res)  // when resetting, we select from the reset val

  io.result := Mux(io.enableFlipflop(0), optionalReg, lookupTable.io.res) // reg is enabled/disabled by enableFlipflop

}

class LogicBlockTests(c: LogicBlock) extends Tester(c) {
  poke(c.io.programmedInputs, 0x8C8C)
  poke(c.io.enableFlipflop, 1)
  poke(c.io.flipflopReset, 0)
  poke(c.io.reset, 0)

  var valuesWithResults = Array(Array(0,0),Array(1,0),Array(2,1),Array(3,1),
    Array(4,0),Array(5,0),Array(6,0),Array(7,1),Array(8,0),Array(9,0),Array(10,1),Array(11,1),
    Array(12,0),Array(13,0),Array(14,0),Array(15,1))

  for (valueWithResult <- valuesWithResults) {
    poke(c.io.selectInputs, valueWithResult(0))
    step(1)
    expect(c.io.result, valueWithResult(1))
  }
}

object LogicBlockTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
      () => Module(new LogicBlock()))
    {
      c => new LogicBlockTests(c)
    }
  }
}