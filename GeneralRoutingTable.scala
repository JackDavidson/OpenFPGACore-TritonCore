import Chisel._
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions._
import java.math.BigInteger

// inCount is the number of Mux selects. outCount is the number of results generated.
class GeneralRoutingTable(myClock: Clock,inCount: Int,outCount: Int,numberOfGroups: Int) extends Module {
  // how to program:
  //   send bits in order of lowest to highest order for selecting -- LSBF
  //     (outputs at the highest order (io.out(outCount-1)) are the bits which you program last) --Highest order last/LSBF
  
  val sizeOfOutputGrouping = outCount/numberOfGroups
  val numberOfInputsToSelFrom = inCount/numberOfGroups
  val numberOfSelectBits = (scala.math.log(numberOfInputsToSelFrom)/scala.math.log(2)).toInt
  val io = new Bundle {
    val in   = Bits(INPUT,  inCount)   // the list of inputs
    val den  = Bool(INPUT)             // Data enable, for shift reg
    val dta  = Bits(INPUT,  1)         // Data in (for programming the shift reg)
    val out  = Bits(OUTPUT, outCount)  // all of the outputs
  }
  val shiftRegisters       = new ArrayBuffer[ShiftRegister]()
  val luts                 = new ArrayBuffer[LUT]()
  val firstShiftReg        = Module(new ShiftRegister(myClock, numberOfSelectBits))
  shiftRegisters          += firstShiftReg
  firstShiftReg.io.enable := io.den
  firstShiftReg.io.dta    := io.dta
  //println("sizeOfOutputGrouping: " + sizeOfOutputGrouping + 
  //        ", numberOfSelectBits: " + numberOfSelectBits +
  //        ", numberOfGroups: " + numberOfGroups +
  //        ", numberOfInputsToSelFrom " + numberOfInputsToSelFrom)
  io.out    := UInt(0) // set a default value for io.out (weird CHISEL req)
  
  for (i <- 1 to numberOfGroups) {
    for (j <- (i-1)*sizeOfOutputGrouping+1 to i*sizeOfOutputGrouping) {
      // TODO: we are creating one extra shift register on the very last loop through
      val shiftReg        = Module(new ShiftRegister(myClock, numberOfSelectBits))
      shiftRegisters     += shiftReg
      shiftReg.io.enable := io.den
      shiftReg.io.dta    := shiftRegisters(j-1).io.out(numberOfSelectBits - 1)
    }
  }
  for (i <- 1 to numberOfGroups) {
    for (j <- (i-1)*sizeOfOutputGrouping+1 to i*sizeOfOutputGrouping) {
      val lut             = Module(new LUT(numberOfSelectBits,1)) // lookup using the select bits
      lut.io.sel         := UInt(0) // set a default value (weird CHISEL req)
      for (k <- 0 to numberOfSelectBits-1)
        lut.io.sel(k)    := shiftRegisters(outCount - j).io.out(numberOfSelectBits - k - 1) // the LUT's select is the shiftReg values
      //lut.io.sel         := shiftRegisters(outCount - j).io.out // the LUT's select is the shiftReg values
      lut.io.lut         := io.in(i*numberOfInputsToSelFrom-1, (i-1)*numberOfInputsToSelFrom)
      io.out(j-1)        := lut.io.res
    }
  }
}

class GeneralRoutingTableTests(c: GeneralRoutingTable) extends Tester(c) {
  // the length of the shft reg in the SRT is selectBits*outCount = 6*960
  // to start off, everything begins as a 0. that means that each output should
  //    map to one of just four of the inputs
  poke(c.io.in, new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16))
  var oneHundredFs = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF" // 100 F's
  var result = ""; // reset the result that we are expecting
  for (i <- 0 to 1) // repeat 100 F's 2 times
    result += oneHundredFs
  result += "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF" // plus 40 more F's
  expect(c.io.out, new BigInteger(result, 16))
  
  // since all the address's are set to 0, we should also get sections of Fs just by setting the 4 1's that everything points to.
  poke(c.io.in, new BigInteger("0000000000000000000000000000000000000000000000000000000001", 16))
  // setting the low-order bit on input should give us all f's on the low-order bits of output
  expect(c.io.out, new BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16))
  
  
  // setting the lowest order bit in the second set of inputs should give us all F's on the second to last quarter of the outputs.
  poke(c.io.in, new BigInteger("0000000000000000000000000000000000000000010000000000000000", 16))
  expect(c.io.out, new BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff000000000000000000000000000000000000000000000000000000000000", 16))
  
  // setting the lowest order bit in the second set of inputs should give us all F's on the second to last quarter of the outputs.
  poke(c.io.in, new BigInteger("0000000001000000000000000000000000000000000000000000000000", 16))
  expect(c.io.out, new BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", 16))
  
  
  
  // now for the complex tests. lets actually program with a custom bitstream. First, put in the address for the highest-order bit.
  poke(c.io.den, 1) // enable sending data. we will not need to disable again for these tests
  
  poke(c.io.dta, 1)
  for(i <- 0 to 19) // 20/6=3, 20%6 = 2, so first 3 output bits point to first input. 4th output bit points to input 
    step(1)         //                                               32 + 16 = 48 (counting from 0 = 49), from the left
  // now we expect the highest-order bit in the highest-order group of inputs to get directed to the highest-order output
  poke(c.io.in, new BigInteger("8000000000000000000000000000000000000000000000000000000000000000", 16))
  result = "E"
  var oneHundredZeros = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
  for (i <- 0 to 1) // repeat 100 0's 2 times
    result += oneHundredZeros
  result += "000000000000000000000000000000000000000" // and 39 0's to finish it up
  expect(c.io.out, new BigInteger(result, 16)) // did it direct correctly?
  
  
  // 20/6=3, 20%6 = 2, so first 3 output bits point to first input. 4th output bit points to input 
  //    32 + 16 = 48 (counting from 0 = 49), from the right. that gives us the 1 below
  poke(c.io.in, new BigInteger("8001000000000000000000000000000000000000000000000000000000000000", 16))
  result = "F"
  for (i <- 0 to 1) // repeat 100 0's 2 times
    result += oneHundredZeros
  result += "000000000000000000000000000000000000000" // and 39 0's to finish it up
  expect(c.io.out, new BigInteger(result, 16)) // did it direct correctly?
}

object GeneralRoutingTableTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness", "--noCombLoop"),
       () => Module(new GeneralRoutingTable(Driver.implicitClock, 256, 960, 4)))
    {
      c => new GeneralRoutingTableTests(c)
    }
  }
}
