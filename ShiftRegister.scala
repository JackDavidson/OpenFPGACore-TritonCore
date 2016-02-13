import Chisel._
import scala.collection.mutable.ArrayBuffer

class ShiftRegister(myClock: Clock) extends Module(myClock) {
  val io = new Bundle {
    val dta = Bits(INPUT,  1)
    val out = Bits(OUTPUT, 8)
  }
  //val myNewReset = addResetPin(Bool())
  //myNewReset.setName("myNewReset")
  //val myClock    = new Clock(Driver.implicitReset, Driver.implicitClock, 1) // note that this clock is delayed by one
  //this.addClock(myClock)

  val r0 = Reg(next = io.dta, init = UInt(0, width = 1), clock = myClock)
  val r1 = Reg(next = r0, init = UInt(0, width = 1), clock = myClock)
  val r2 = Reg(next = r1, init = UInt(0, width = 1), clock = myClock)
  val r3 = Reg(next = r2, init = UInt(0, width = 1), clock = myClock)
  val r4 = Reg(next = r3, init = UInt(0, width = 1), clock = myClock)
  val r5 = Reg(next = r4, init = UInt(0, width = 1), clock = myClock)
  val r6 = Reg(next = r5, init = UInt(0, width = 1), clock = myClock)
  val r7 = Reg(next = r6, init = UInt(0, width = 1), clock = myClock)

  io.out    := UInt(0)
  io.out(0) := r0
  io.out(1) := r1
  io.out(2) := r2
  io.out(3) := r3
  io.out(4) := r4
  io.out(5) := r5
  io.out(6) := r6
  io.out(7) := r7
}

class ShiftRegisterTests(c: ShiftRegister, clk: Clock) extends Tester(c) {
  var values = Array(1,0,1,0,0,1,1,1)
  poke(c.io.dta, 0)
  for (value <- values) {
    poke(c.io.dta, value)
    step(1)
  }
  //step(1) // we step first because myClock is delayed (I think this is a CHISEL bug)
  expect(c.io.out, 0xA7)
}

object ShiftRegisterTestRunner {
  def main(args: Array[String]): Unit = {


    //val reg        = Reg(init=Bool(false), clock=myClock)
    //val myClock = new Clock()
    //val myClock    = new Clock(Driver.implicitReset, Driver.implicitClock, 1)


    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
       () => Module(new ShiftRegister(Driver.implicitClock))) // implicitClock is the default clock
    {
      c => new ShiftRegisterTests(c, Driver.implicitClock) // implicitClock is the default clock
    }
  }
}
