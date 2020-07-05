import java.net.URLEncoder

class Address(val query: String) {
    val googleMapsLink = "https://www.google.com/maps/search/" + URLEncoder.encode(query, "utf-8")

    override fun toString(): String {
        return query + "\n" + googleMapsLink + "\n";
    }
}