import Chisel._

/**
  * the MultiplyBlock is a wrapper around a multipliation unit
  *   routed inputs:  16   (inputA, inputB)
  *   routed outputs: 16   (result)
  *   programming:     0
  *   other inputs:    0
  */
class WrapperMultiplier extends Module {
  val io = new Bundle {
    val inputA      = Bits(INPUT,  8 ) // the first input number, unsigned
    val inputB      = Bits(INPUT,  8 ) // the second input number, unsigned
    val enableMult  = Bits(INPUT,  1 )
    val enableReg   = Bits(INPUT,  1 )
    val result      = Bits(OUTPUT, 16) // the multiplication result. unsigned
  }
  val optionalReg = Reg(init = UInt(0, width = 16))
  val multResult = io.inputA * io.inputB

  val concatenated = UInt(width = 16)
  concatenated := Cat(io.inputA, io.inputB)
  val result = UInt(width = 16)
  result := Mux(io.enableMult(0), multResult, concatenated)
  optionalReg := result

  io.result := Mux(io.enableReg(0), optionalReg, result)
}

class WrapperMultiplierTests(c:WrapperMultiplier) extends Tester(c) {
  poke(c.io.inputA, 0xAA)
  poke(c.io.enableMult, 1)

  var valuesWithResults = Array(Array(0xAA, 0x70E4), Array(0x6F, 0x49B6))

  for (valueWithResult <- valuesWithResults) {
    poke(c.io.inputB, valueWithResult(0))
    expect(c.io.result, valueWithResult(1))
  }
}

object WrapperMultiplierTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
      () => Module(new WrapperMultiplier()))
    {
      c => new WrapperMultiplierTests(c)
    }
  }
}