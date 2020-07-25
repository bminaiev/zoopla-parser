
class RentCost(text: String) {
    val pricePoundsPerMonth = parseMonthPrice(text) ?: 0

    override fun toString(): String {
        return pricePoundsPerMonth.toString() + "£\n";
    }

    fun isOutOfRange(): Boolean {
        return pricePoundsPerMonth > 3500 || pricePoundsPerMonth < 1500
    }
}