package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V2__ReservationOwnershipAndIndexes extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 20261003; }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String table = "reservations";
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if ("reservations".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    table = tables.getString("TABLE_NAME"); break;
                }
            }
        }
        boolean ownerExists = false;
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, "%")) {
            while (columns.next()) ownerExists |= "owner_id".equalsIgnoreCase(columns.getString("COLUMN_NAME"));
        }
        try (Statement statement = connection.createStatement()) {
            if (!ownerExists) statement.execute("ALTER TABLE reservations ADD COLUMN owner_id VARCHAR(73) NULL");
            addIndex(connection, statement, table, "idx_reservation_owner", "owner_id");
            addIndex(connection, statement, table, "idx_reservation_unit_dates", "unit_id,status,check_in,check_out");
        }
    }

    private void addIndex(Connection connection, Statement statement, String table, String name, String columns) throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) if (name.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return;
        }
        statement.execute("CREATE INDEX " + name + " ON reservations (" + columns + ")");
    }
}
