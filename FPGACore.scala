import Chisel._

class FPGACore extends Module {
  val io = new Bundle {}
  printf("Hello World!\n")
}

class FPGACoreTests(c: FPGACore) extends Tester(c) {
  step(1)
}

object coreTestRunner {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array[String]("--backend", "c", "--compile", "--test", "--genHarness"),
       () => Module(new FPGACore())){c => new FPGACoreTests(c)}
  }
}
