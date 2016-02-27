import Chisel._
import scala.collection.mutable.ArrayBuffer

class ShiftRegisteredLogicCell4(myClock: Clock) extends Module {
  // how to program this: first send ffen, then reVal, then LUT data LSB first
  // ffen is flipflop enable, and selects whether or not to register the results of the LUT.
  // reVal is the value to reset the LC's FF to.
  // sel(3) is the high-order bit. sel(0) is the low-order
  val io = new Bundle {
    val den   = Bool(INPUT)     // data enable for shift register
    val dta   = Bits(INPUT,  1) // the data being sent to shift register. only shifts when(den)
    val sel   = Bits(INPUT,  4) // the 4 bits used as selects in the LUT
    val reset = Bool(INPUT)     // resets the FF in the LC to the reset bit
    val res   = Bits(OUTPUT, 1) // the one-bit result
    val cot   = Bits(OUTPUT, 1) // the carry-out of the shift register
  }
  val shiftReg        = Module(new ShiftRegister(myClock, 18))
  shiftReg.io.enable := io.den // den just gets passed to the shift register
  shiftReg.io.dta    := io.dta // same with data
  
  
  val logicCell       = Module(new LogicCell4()) // create the logic cell
  logicCell.io.lut   := UInt(0);                 // that weird CHISEL default value req again.
                                                 // CHISEL really needs to get rid of this....
  for (i <- 0 to 15) {
    // the last 16 bits to be sent to the shift reg program the LUT for logic cell
    // note that this ordering also means that data is sent to the shift register LUT from LSB to MSB
    logicCell.io.lut(i) := shiftReg.io.out(15 - i);
    
    // WHY DOESNT THIS NEXT LINE WORK??!!? :
    // logicCell.io.lut(15 - i) := shiftReg.io.out(i);
    // TODO: there seems to be a CHISEL bug there!
  }
  logicCell.io.reVal := shiftReg.io.out(16)      // set the FF reset value in the LC
  logicCell.io.ffen  := shiftReg.io.out(17)      // enable or disable the FF in the LC
  logicCell.io.reset := io.reset | io.den        // connect the  FF reset wire or'd with data enable
  logicCell.io.sel := io.sel
  io.res           := logicCell.io.res
  io.cot           := shiftReg.io.out(15)
}

class ShiftRegisteredLogicCell4Tests(c: ShiftRegisteredLogicCell4) extends Tester(c) {
  /* basic lookup tests. note that this tester parallels the LogicCell4 tester */
  /* please look at LogicCell4Tests if you are confused by this tester              */
  // poke(c.io.lut, 0xAFCD)
  // poke(c.io.reset, 0)
  // poke(c.io.ffen, 0)
  var shiftRegVals : Long = ((0xAFCD.toLong << 2.toLong) | 0x0.toLong);
  poke(c.io.den, 1) // === BEGIN  SEND DATA ===
  for (i <- 0 to 17) {
    // send in LSB to MSB
    poke(c.io.dta, (shiftRegVals & (0x1 << i)) >> i)
    step(1)
  }
  poke(c.io.den, 0) // === FINISH SEND DATA ===
  
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
  // poke(c.io.ffen, 1) /* enable the register */
  // poke(c.io.reVal, 0) /* set reset val to 0 */
  shiftRegVals = ((0xAFCD.toLong << 2.toLong) | 0x1.toLong);
  poke(c.io.den, 1) // === BEGIN  SEND DATA ===
  for (i <- 0 to 17) {
    // send in LSB to MSB
    poke(c.io.dta, (shiftRegVals & (0x1 << i)) >> i)
    step(1)
  }
  poke(c.io.den, 0) // === FINISH SEND DATA ===
  
  for (valueWithResult <- valuesWithResults) {
    step(1)                            /* load the value into reg */
    poke(c.io.sel, valueWithResult(0)) /* put in the next sel (shouldn't change the resut) */
    expect(c.io.res, previousVal)      /* check that the value, indeed, has not changed */
    previousVal = valueWithResult(1)   /* assign the next expected value for next iteration */
  }
  
  
  
  
  
  
   /* check the reset */
  poke(c.io.reset, 1) // enable reset
  //poke(c.io.reVal, 0) // set reset val to 0 (already set earlier)
  step(1)
  expect(c.io.res, 0) // did the result change?
  
  // poke(c.io.reVal, 1) // set reset val to 1
  shiftRegVals = ((0xAFCD.toLong << 2.toLong) | 0x3.toLong);
  poke(c.io.den, 1) // === BEGIN  SEND DATA ===
  for (i <- 0 to 17) {
    // send in LSB to MSB
    poke(c.io.dta, (shiftRegVals & (0x1 << i)) >> i)
    step(1)
  }
  poke(c.io.den, 0) // === FINISH SEND DATA ===
  step(1)
  expect(c.io.res, 1) // did the result change?
  
  // poke(c.io.reVal, 1) // set reset val to 1
  shiftRegVals = ((0xFFFF.toLong << 2.toLong) | 0x1.toLong);
  poke(c.io.den, 1) // === BEGIN  SEND DATA ===
  for (i <- 0 to 17) {
    // send in LSB to MSB
    poke(c.io.dta, (shiftRegVals & (0x1 << i)) >> i)
    step(1)
  }
  poke(c.io.den, 0) // === FINISH SEND DATA ===
  step(1)
  expect(c.io.res, 0) // did the result change?
}

object ShiftRegisteredLogicCell4TestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
       () => Module(new ShiftRegisteredLogicCell4(Driver.implicitClock))) // implicitClock is the default clock
    {
      c => new ShiftRegisteredLogicCell4Tests(c) // implicitClock is the default clock. Used to test
    }
  }
}
