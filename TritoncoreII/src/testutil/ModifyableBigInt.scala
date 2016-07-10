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
  override def toString() = value.toString(16)
}