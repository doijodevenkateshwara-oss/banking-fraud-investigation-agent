package com.bfs.fraudcase.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

/**
 * H2 trigger that rejects UPDATE and DELETE on the audit table.
 *
 * @author Venkateshwara Doijode
 */
public class AppendOnlyTrigger implements Trigger {

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("audit_event is append-only");
    }
}
