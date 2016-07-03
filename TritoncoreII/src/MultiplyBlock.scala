import Chisel._


class MultiplyBlock extends Module {
  val io = new Bundle {
    val inputA      = Bits(INPUT,  8 ) // the first input number, unsigned
    val inputB      = Bits(INPUT,  8 ) // the second input number, unsigned
    val result      = Bits(OUTPUT, 16) // the multiplication result. unsigned
  }
  io.result := io.inputA * io.inputB
}

class MultiplyBlockTests(c:MultiplyBlock) extends Tester(c) {
  poke(c.io.inputA, 0xAA)

  var valuesWithResults = Array(Array(0xAA, 0x70E4), Array(0x6F, 0x49B6))

  for (valueWithResult <- valuesWithResults) {
    poke(c.io.inputB, valueWithResult(0))
    expect(c.io.result, valueWithResult(1))
  }
}

object MultiplyBlockTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
      () => Module(new MultiplyBlock()))
    {
      c => new MultiplyBlockTests(c)
    }
  }
}