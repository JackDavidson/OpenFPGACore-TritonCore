class ModifyableBigInt() {
  var value = BigInt(0)
  def setBits(hi : Int, lo : Int, newValue : Int) {
    for (i <- lo to hi) {
      val newValIndex = i - lo
      if ((newValue & (1 << newValIndex)) != 0) {
        value = value.setBit(i)
      } else {
        value = value.clearBit(i)
      }
    }
  }
  def get32(index: Int): Int = {
    var result = 0
    for (i <- 0 to 31) {
      if (value.testBit(index*32 + i)) {
        result |= 1 << i
      }
    }
    return result
  }
  override def toString() = value.toString(16)
}