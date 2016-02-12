import Chisel._

class LUT extends Module {
  val io = new Bundle {
    val lut = Bits(INPUT,  4)
    val sel = Bits(INPUT,  2)
    val res = Bits(OUTPUT, 1)
  }
  val highOrLow  = Mux(io.sel(1), io.lut(3,2), io.lut(1,0))
  io.res        := Mux(io.sel(0), highOrLow(1), highOrLow(0))
}

class LUTTests(c: LUT) extends Tester(c) {
  poke(c.io.lut, 12)

  var valuesWithResults = Array(Array(0,0),Array(1,0),Array(2,1),Array(3,1))

  for (valueWithResult <- valuesWithResults) {
    poke(c.io.sel, valueWithResult(0))
    step(1)
    expect(c.io.res, valueWithResult(1))
  }
}

object LUTTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
       () => Module(new LUT()))
    {
      c => new LUTTests(c)
    }
  }
}
