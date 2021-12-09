
class RentCost(text: String) {
    val pricePoundsPerMonth = parseMonthPrice(text) ?: 0

    override fun toString(): String {
        return pricePoundsPerMonth.toString() + "£\n";
    }

    companion object {
        const val DEFAULT_MIN_PRICE = 1500
        const val DEFAULT_MAX_PRICE = 4000
    }

}