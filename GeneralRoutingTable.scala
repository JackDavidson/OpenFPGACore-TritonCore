import Chisel._
import scala.collection.mutable.ArrayBuffer

// inCount is the number of Mux selects. outCount is the number of results generated.
class GeneralRoutingTable(myClock: Clock,inCount: Int,outCount: Int,numberOfGroups: Int) extends Module {
  val sizeOfOutputGrouping = outCount/numberOfGroups
  val numberOfInputsToSelFrom = inCount/numberOfGroups
  val numberOfSelectBits = (scala.math.log(numberOfInputsToSelFrom)/scala.math.log(2)).toInt
  val io = new Bundle {
    val in   = Bits(INPUT,  inCount)   // the list of inputs
    val den  = Bool(INPUT)             // Data enable, for shift reg
    val dta  = Bits(INPUT,  1)         // Data in (for programming the shift reg)
    val dout = Bits(OUTPUT, 1)         // Data out (for carrying out)
    val out  = Bits(OUTPUT, outCount)  // all of the outputs
  }
  val shiftRegisters       = new ArrayBuffer[ShiftRegister]()
  val luts                 = new ArrayBuffer[LUT]()
  val firstShiftReg        = Module(new ShiftRegister(myClock, numberOfSelectBits))
  shiftRegisters          += firstShiftReg
  firstShiftReg.io.enable := io.den
  firstShiftReg.io.dta    := io.dta
  println("sizeOfOutputGrouping: " + sizeOfOutputGrouping + 
          ", numberOfSelectBits: " + numberOfSelectBits +
          ", numberOfGroups: " + numberOfGroups +
          ", numberOfInputsToSelFrom " + numberOfInputsToSelFrom)
  io.out    := UInt(0) // set a default value for io.out (weird CHISEL req)
  
  for (i <- 1 to numberOfGroups) {
    for (j <- (i-1)*sizeOfOutputGrouping+1 to i*sizeOfOutputGrouping) {
      val shiftReg        = Module(new ShiftRegister(myClock, numberOfSelectBits))
      shiftRegisters     += shiftReg
      shiftReg.io.enable := io.den
      shiftReg.io.dta    := shiftRegisters(j-1).io.out(numberOfSelectBits - 1)
      
      val lut             = Module(new LUT(numberOfSelectBits,1)) // lookup using the select bits
      lut.io.sel         := shiftReg.io.out // the LUT's select is the shiftReg values
      lut.io.lut         := io.in(i*numberOfInputsToSelFrom-1, (i-1)*numberOfInputsToSelFrom)
      io.out(j-1)        := lut.io.res
    }
  }
}

class GeneralRoutingTableTests(c: GeneralRoutingTable) extends Tester(c) {
  // the length of the shft reg in the SRT is selectBits*outCount = 6*960
  // to start off, everything begins as a 0. that means that each output should
  //    map to one of just four of the inputs
  
  
  
  
  // first we program i0 (LSB) on LUT 0 (LUT0 produces output 0, the 1st of 16 outputs)
  
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
