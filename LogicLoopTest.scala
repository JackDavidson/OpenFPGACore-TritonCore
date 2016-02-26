import Chisel._
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions._
import java.math.BigInteger

// this is just a test that shows how combinmational loops are allowed in scala, as long as you don't try to test them
// that means, as long as you ensure that no combinational loops actualy happen during testing,
// chisel can actually implement them using the '--noCombLoop' option
// if you like, try removing "--noCombLoop" and CHISEL will tell you that there is a combinational loop
class LogicLoop() extends Module {
  
  val io = new Bundle {
    val in   = Bits(INPUT,  1)
    val den  = Bool(INPUT)
    val dta  = Bits(INPUT,  1)
    val dout = Bits(OUTPUT, 1)
    val out  = Bits(OUTPUT, 1)
  }
  // connect a loop using a shift register
  val a   = Reg(init = UInt(0, width = 1)) // the state register\
  val b   = UInt()
  b := a
  val sel = Reg(init = UInt(1, width = 1)) // selects whether to connect the output of a to itself or register its inverse
  when(Bool(sel)) {
    a := ~b    // if sel is 1, then invert a.
  } otherwise {
    b := b     // otherwise connect wire b directly to itself
  }
  io.out := b
}

class LogicLoopTests(c: LogicLoop) extends Tester(c) {
  peek(c.io.out)
  step(1)
  peek(c.io.out)
  step(1)
  peek(c.io.out)
  step(1)
  peek(c.io.out)
  step(1)
  peek(c.io.out)
  step(1)

}

object LogicLoopTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness", "--noCombLoop"),
       () => Module(new LogicLoop()))
    {
      c => new LogicLoopTests(c)
    }
  }
}
