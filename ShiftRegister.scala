import Chisel._
import scala.collection.mutable.ArrayBuffer

class ShiftRegister(myClock: Clock, width: Int) extends Module(myClock) {
  // note that values go in at out(0) and get pushed up to out(width - 1)
  val io = new Bundle {
    val dta = Bits(INPUT,  1)
    val out = Bits(OUTPUT, width)
  }
  val registers = new ArrayBuffer[UInt]()
  registers += Reg(next = io.dta, init = UInt(0, width = 1), clock = myClock)

  io.out    := UInt(0) // set a default value for io.out (weird CHISEL req)
  
  for (i <- 1 to width) { /* soooo much easier than verilog */
    registers   += Reg(next = registers(i-1), init = UInt(0, width = 1), clock = myClock)
    io.out(i-1) := registers(i-1) // connect the just-creaed Reg to the output
  }
}

class ShiftRegisterTests(c: ShiftRegister, clk: Clock) extends Tester(c) {
  var values = Array(1,0,1,0,0,1,1,1) // values to push into the register
  poke(c.io.dta, 0)
  for (value <- values) {
    poke(c.io.dta, value)
    step(1)  // push '10100111' through the shift register. step toggles clk twice.
  }
  expect(c.io.out, 0xA7) // did we get the correc result?
}

object ShiftRegisterTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
       () => Module(new ShiftRegister(Driver.implicitClock, 8))) // implicitClock is the default clock
    {
      c => new ShiftRegisterTests(c, Driver.implicitClock) // implicitClock is the default clock. Used to test
    }
  }
}
