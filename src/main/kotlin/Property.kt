data class Property(
    val link: String,
    val imgs: Array<String>,
    val costPerMonth: RentCost,
    val floorPlanImage: String,
    val address: Address,
    val id: Int,
    val searchTag: QueryParams,
    val areaSqM: Double?
)