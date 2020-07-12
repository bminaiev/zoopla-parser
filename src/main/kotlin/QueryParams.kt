data class QueryParams(val baseUrl: String, val tag: String) {
    override fun toString(): String {
        if (tag.isEmpty()) {
            return "";
        } else {
            return "<b>tag:</b> $tag\n";
        }
    }

    companion object {
        val EMPTY = QueryParams("", "");
    }
}