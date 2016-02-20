import Chisel._
import scala.collection.mutable.ArrayBuffer

// inCount is the number of Mux selects. outCount is the number of results generated.
class LogicCell4() extends Module {
  val io = new Bundle {
    val lut   = Bits(INPUT,  16)// the 16 bits used as lookup values for the LUT
    val sel   = Bits(INPUT,  4) // the 4 bits used as selects in the LUT
    val reVal = Bits(INPUT,  1) // the value to set the flipflop to when its reset
    val ffen  = Bool(INPUT)     // enable this cell's flipflop?
    val reset = Bool(INPUT)     // set to true and raise the clock to reset the FF
    val res   = Bits(OUTPUT, 1) // the one bit output from this Logic cell
  }
  val lutImpl     = Module(new LUT(4,1));
  val optionalReg = Reg(init = UInt(0, width = 1));
  lutImpl.io.lut := io.lut;
  lutImpl.io.sel := io.sel;
  when (io.reset) {
    optionalReg := io.reVal;
  } .otherwise {
    optionalReg := lutImpl.io.res;
  }
  io.res := Mux(io.ffen, optionalReg, lutImpl.io.res);
}

class LogicCell4Tests(c: LogicCell4) extends Tester(c) {
  poke(c.io.lut, 0xAFCD)
  poke(c.io.reset, 0) /* disable reset (not actually necessary) */
  poke(c.io.ffen, 0)  /* disable flipflop (not actually necessary) */

  /* LSB is at position 0. MSB is at 15 (F) */
  var valuesWithResults = Array(Array(0x0,1),Array(0x1,0),Array(0x2,1),Array(0x3,1), // D reversed
                                Array(0x4,0),Array(0x5,0),Array(0x6,1),Array(0x7,1), // C reversed
                                Array(0x8,1),Array(0x9,1),Array(0xA,1),Array(0xB,1), // F reversed
                                Array(0xC,0),Array(0xD,1),Array(0xE,0),Array(0xF,1)) // A reversed
                                
  for (valueWithResult <- valuesWithResults) {
    poke(c.io.sel, valueWithResult(0))
    //step(1)
    expect(c.io.res, valueWithResult(1))
  }
  
  /* test with the register */
  var previousVal = valuesWithResults(valuesWithResults.length - 1)(1)
  poke(c.io.ffen, 1) /* enable the register */
  
  for (valueWithResult <- valuesWithResults) {
    step(1)                            /* load the value into reg */
    poke(c.io.sel, valueWithResult(0)) /* put in the next sel (shouldn't change the resut) */
    expect(c.io.res, previousVal)      /* check that the value, indeed, has not changed */
    previousVal = valueWithResult(1)   /* assign the next expected value for next iteration */
  }
  
  /* check the reset */
  poke(c.io.reset, 1) // enable reset
  poke(c.io.reVal, 0) // set reset val to 0
  step(1)
  expect(c.io.res, 0) // did the result change?
  
  poke(c.io.reVal, 1) // set reset val to 1
  expect(c.io.res, 0) // double check that it doesn't change until we step
  step(1)
  expect(c.io.res, 1) // did the result change?
  
  poke(c.io.reVal, 0) // set reset val to 0
  expect(c.io.res, 1) // double check that it doesn't change until we step
  step(1)
  expect(c.io.res, 0) // did the result change?
  
  
}

object LogicCell4TestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
       () => Module(new LogicCell4()))
    {
      c => new LogicCell4Tests(c)
    }
  }
}
