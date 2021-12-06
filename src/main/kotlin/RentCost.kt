
class RentCost(text: String) {
    val pricePoundsPerMonth = parseMonthPrice(text) ?: 0

    override fun toString(): String {
        return pricePoundsPerMonth.toString() + "£\n";
    }

    // TODO: remove this function?
    fun isOutOfRange(): Boolean {
        return pricePoundsPerMonth > 8000 || pricePoundsPerMonth < 1500
    }
}