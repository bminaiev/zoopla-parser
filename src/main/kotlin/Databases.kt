import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object SeenPropertiesTable : Table() {
    val id = integer("id")
    val user_name = varchar("user_name", length = 50)

    override val primaryKey = PrimaryKey(id, user_name)
}

object skipped_properties : Table() {
    val id = integer("id")

    override val primaryKey = PrimaryKey(id)
}

object QueryParamsTable : Table() {
    val id = integer("id").autoIncrement()
    val tag = varchar("tag", length = 50)
    val queryUrl = text("query_url")
    val minPrice = integer("min_price").nullable()
    val maxPrice = integer("max_price").nullable()

    override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table() {
    val name = varchar("name", length = 50)
    val chatId = long("chat_id")

    override val primaryKey = PrimaryKey(name)
}

object SubscriptionsTable : Table() {
    val userName = varchar("user", length = 50).references(UsersTable.name)
    val queryParamsId = integer("query_params_id")

    override val primaryKey = PrimaryKey(userName, queryParamsId)
}


class Databases {
    companion object {
        const val BORYS = "Borys"
        const val ANTON = "Anton"
        const val SOFIA = "Sofia"

        private val tablesToRecreate = arrayOf(QueryParamsTable, UsersTable, SubscriptionsTable)

        fun createTables() {
            SchemaUtils.create(*tablesToRecreate)
        }

        fun init() {
            transaction {
                createTables()
            }
        }

        private fun insertUsersTable() {
            UsersTable.insert {
                it[name] = BORYS
                it[chatId] = 24273498
            }
            UsersTable.insert {
                it[name] = ANTON
                it[chatId] = 140064432
            }
            UsersTable.insert {
                it[name] = SOFIA
                it[chatId] = 268357650
            }
        }

        private fun insertLondonBridge() {
            val id = QueryParamsTable.insert {
                it[tag] = "London Bridge"
                it[minPrice] = 2000
                it[maxPrice] = 8000
                it[queryUrl] = "/to-rent/property/station/rail/london-bridge/?q=London%20Bridge%20Station%2C%20London"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = BORYS
                it[queryParamsId] = id
            }
            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
        }

        private fun insertFarringdon() {
            val id = QueryParamsTable.insert {
                it[tag] = "Farringdon"
                it[queryUrl] = "/to-rent/property/london/britton-street/ec1m-5ny/?q=ec1m%205ny&radius=1"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = BORYS
                it[queryParamsId] = id
            }
            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
        }

        private fun insertAngel() {
            val id = QueryParamsTable.insert {
                it[tag] = "Angel"
                it[minPrice] = 2000
                it[maxPrice] = 8000
                it[queryUrl] = "/to-rent/property/angel/?q=Angel%2C%20London&radius=1"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = BORYS
                it[queryParamsId] = id
            }
            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
            SubscriptionsTable.insert {
                it[userName] = SOFIA
                it[queryParamsId] = id
            }
        }

        private fun insertKingsCross() {
            val id = QueryParamsTable.insert {
                it[tag] = "Kings Cross"
                it[minPrice] = 2000
                it[maxPrice] = 8000
                it[queryUrl] = "/to-rent/property/london/kings-cross/?q=Kings%20Cross%2C%20London&radius=1"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = BORYS
                it[queryParamsId] = id
            }
            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
        }

        private fun insertLiverpoolStreet() {
            val id = QueryParamsTable.insert {
                it[tag] = "Liverpool Street"
                it[minPrice] = 2000
                it[maxPrice] = 8000
                it[queryUrl] = "/to-rent/property/liverpool-street/?q=Liverpool%20Street%2C%20London&radius=1"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = BORYS
                it[queryParamsId] = id
            }
            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
        }

        private fun insertTowerBridge() {
            val id = QueryParamsTable.insert {
                it[tag] = "Tower Bridge"
                it[minPrice] = 2000
                it[maxPrice] = 8000
                it[queryUrl] = "/to-rent/property/tower-bridge/?q=Tower%20Bridge%2C%20London&radius=1"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = BORYS
                it[queryParamsId] = id
            }
            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
        }

        private fun insertVauxhall() {
            val id = QueryParamsTable.insert {
                it[tag] = "Vauxhall"
                it[queryUrl] = "/to-rent/property/station/tube/waterloo/?q=Waterloo%20Station%2C%20London&radius=1"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
        }

        private fun insertElephantAndCastle() {
            val id = QueryParamsTable.insert {
                it[tag] = "Elephant And Castle"
                it[queryUrl] =
                    "/to-rent/property/station/rail/elephant-and-castle-underground/?q=Elephant%20%26%20Castle%20(Underground)%20Station%2C%20London&radius=1"
            }[QueryParamsTable.id]

            SubscriptionsTable.insert {
                it[userName] = ANTON
                it[queryParamsId] = id
            }
        }

        private fun insertQueryParams() {
            insertLondonBridge()
            insertFarringdon()
            insertAngel()
            insertKingsCross()
            insertLiverpoolStreet()
            insertTowerBridge()
            insertVauxhall()
            insertElephantAndCastle()
        }

        private fun insertDefaultValues() {
            insertUsersTable()
            insertQueryParams()
        }

        fun reinitialize() {
            transaction {
                SchemaUtils.drop(*tablesToRecreate)
                createTables()
                insertDefaultValues()
            }
        }
    }
}