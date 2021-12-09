data class Property(
    val link: String,
    val imgs: Array<String>,
    val costPerMonth: RentCost,
    val floorPlanImage: String,
    val address: Address,
    val id: Int,
    val areaSqM: Double?
) {
    companion object {
        fun existReasonToSkip(property: Property): String? {
            if (property.areaSqM == null) {
                return "Skip propery because can't compute area"
            }
            if (FloorPlanOCR.tooSmallArea(property.areaSqM)) {
                return "Skip property because area is too small: ${property.areaSqM}"
            }
            return null
        }
    }
}
