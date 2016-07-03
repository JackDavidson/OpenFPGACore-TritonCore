import Chisel._

/**
  * the WrapperMemory is a wrapper around a 16-bit address and with memory. There are two read ports and two write ports
  *   routed inputs:  65   (addressA, addressB, writeAddress, writeEnable, writeData)
  *   routed outputs: 32   (resultA, resultB)
  *   programming:     0
  *   other inputs:    0
  */
class WrapperMemory extends Module {
  val io = new Bundle {
    val addressA     = Bits(INPUT,  16) // first read address
    val addressB     = Bits(INPUT,  16) // second read address
    val writeAddress = Bits(INPUT,  16) // the write address
    val writeEnable  = Bits(INPUT,  1 )
    val writeData    = Bits(INPUT,  16) // write port data
    val resultA      = Bits(OUTPUT, 16) // first read result
    val resultB      = Bits(OUTPUT, 16) // second read result
  }
  val mem = Mem(UInt(width = 16), 65536, seqRead = true) // 16 bits per entry, 65536 entries, read on clock
  val resultA = Reg(UInt())
  val resultB = Reg(UInt())
  when (io.writeEnable(0)) {
    mem(io.writeAddress) := io.writeData
  }
  resultA := mem(io.addressA)
  resultB := mem(io.addressB)
  io.resultA := resultA
  io.resultB := resultB
}

class WrapperMemoryTests(c: WrapperMemory) extends Tester(c) {
  val values = Array(0x0000, 0xA000, 0xBBAA)

  poke(c.io.writeEnable, 1)
  for ((value, i) <- values.zipWithIndex) { // store our array
    poke(c.io.writeAddress, i)
    poke(c.io.writeData, values(i))
    step(1)
  }

  poke(c.io.writeEnable, 0)
  for ((value, i) <- values.zipWithIndex) {
    poke(c.io.addressA, i)
    poke(c.io.addressB, i)
    step(1)
    expect(c.io.resultA, value) // the outputs should match with what we put in
    expect(c.io.resultB, value)
  }
  for ((value, i) <- values.zipWithIndex) {
    poke(c.io.addressA, i)
    poke(c.io.addressB, i)                          // poke different values
    expect(c.io.resultA, values(values.length - 1)) // expect the output to remain the same (only updates on clk)
    expect(c.io.resultB, values(values.length - 1))
  }
  poke(c.io.addressA, 0)
  poke(c.io.addressB, 1)
  poke(c.io.writeAddress, 0)
  poke(c.io.writeData, values(1))
  poke(c.io.writeEnable, 1)
  step(1)
  expect(c.io.resultA, values(0))
  expect(c.io.resultB, values(1))
  step(1)
  expect(c.io.resultA, values(1))
  expect(c.io.resultB, values(1))

}

object WrapperMemoryTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
      () => Module(new WrapperMemory()))
    {
      c => new WrapperMemoryTests(c)
    }
  }
}