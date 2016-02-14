import Chisel._
import scala.collection.mutable.ArrayBuffer

// inCount is the number of Mux selects. outCount is the number of results generated.
class LUT(inCount: Int, outCount: Int) extends Module {
  val lutWidth = (scala.math.pow(2, inCount)*outCount).toInt
  val io = new Bundle {
    val lut = Bits(INPUT,  lutWidth)
    val sel = Bits(INPUT,  inCount)
    val res = Bits(OUTPUT, outCount)
  }
  val muxs = new ArrayBuffer[UInt]()
  var lastWidth = lutWidth // used to select high vs. low in 
  muxs += Mux(io.sel(inCount - 1), io.lut(lastWidth-1, lastWidth/2), io.lut(lastWidth/2 - 1, 0))
  lastWidth = lastWidth / 2
  for (i <- 1 to inCount) { /* soooo much easier than verilog */
    muxs += Mux(io.sel(inCount - i - 1), muxs(i-1)(lastWidth-1, lastWidth/2), muxs(i-1)(lastWidth/2 - 1, 0))
  }
  
  // result is simply the last Mux. note that this means that results to the functions
  // are interleaved. lut = f1[0]f2[0]f1[1]f2[1]f1[2]f2[2]...
  io.res        := muxs(muxs.length - 1)
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
       () => Module(new LUT(2, 1)))
    {
      c => new LUTTests(c)
    }
  }
}
