/*
 * Copyright 2004-2018 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import org.h2.command.dml.SetTypes;
import org.h2.test.TestBase;
import org.h2.test.TestDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test subquery performance with lazy query execution mode {@link SetTypes#LAZY_QUERY_EXECUTION}.
 */
public class TestSubqueryPerformanceOnLazyExecutionMode extends TestDb {
    /** Rows count. */
    private static final int ROWS = 5000;
    /** Test repeats when unexpected failure. */
    private static final int FAIL_REPEATS = 5;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String[] a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws Exception {
        deleteDb("lazySubq");
        try (Connection conn = getConnection("lazySubq")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE one (x INTEGER, y INTEGER )");
                try (PreparedStatement prep = conn.prepareStatement("insert into one values (?,?)")) {
                    for (int row = 0; row < ROWS; row++) {
                        prep.setInt(1, row / 100);
                        prep.setInt(2, row);
                        prep.execute();
                    }
                }

                testSubqueryInCondition(stmt);
                testSubqueryInJoin(stmt);
                testSubqueryInJoinFirst(stmt);
                testJoinTwoSubqueries(stmt);
            }
        }
        finally {
            deleteDb("lazySubq");
        }
    }

    public void testSubqueryInCondition(Statement stmt) throws Exception {
        String sql = "SELECT COUNT (*) FROM one WHERE x IN (SELECT y FROM one WHERE y < 50)";

        checkExecutionTime(stmt, sql);
    }

    public void testSubqueryInJoin(Statement stmt) throws Exception {
        String sql =
                "SELECT COUNT (one.x) FROM one " +
                "JOIN (SELECT y AS val FROM one WHERE y < 50) AS subq ON subq.val=one.x";

        checkExecutionTime(stmt, sql);
    }

    public void testSubqueryInJoinFirst(Statement stmt) throws Exception {
        String sql =
                "SELECT COUNT (one.x) FROM " +
                        "(SELECT y AS val FROM one WHERE y < 50) AS subq " +
                        "JOIN one ON subq.val=one.x";

        checkExecutionTime(stmt, sql);
    }

    public void testJoinTwoSubqueries(Statement stmt) throws Exception {
        String sql =
                "SELECT COUNT (one_sub.x) FROM " +
                        "(SELECT y AS val FROM one WHERE y < 50) AS subq " +
                        "JOIN (SELECT x FROM one) AS one_sub ON subq.val=one_sub.x";

        checkExecutionTime(stmt, sql);
    }

    /**
     * Compare execution time when lazy execution mode is disabled and enabled.
     * The execution time must be almost the same.
     */
    private void checkExecutionTime(Statement stmt, String sql) throws Exception {
        long totalNotLazy = 0;
        long totalLazy = 0;

        int successCnt = 0;
        int failCnt = 0;

        for (int i = 0; i < FAIL_REPEATS; ++i) {
            long tNotLazy = executeAndCheckResult(stmt, sql, false);
            long tLazy = executeAndCheckResult(stmt, sql, true);

            totalNotLazy += tNotLazy;
            totalLazy += tLazy;

            if (tNotLazy * 2 > tLazy) {
                successCnt++;
                if (i == 0) {
                    break;
                }
            } else {
                failCnt++;
            }
        }

        if (failCnt > successCnt) {
           fail("Lazy execution too slow. Avg lazy time: "
                            + (totalLazy / FAIL_REPEATS) + ", avg not lazy time: " + (totalNotLazy / FAIL_REPEATS));
        }
    }

    /**
     * @return Time of the query execution.
     */
    private long executeAndCheckResult(Statement stmt, String sql, boolean lazy) throws SQLException {
        if (lazy) {
            stmt.execute("SET LAZY_QUERY_EXECUTION 1");
        }
        else {
            stmt.execute("SET LAZY_QUERY_EXECUTION 0");
        }

        long t0 = System.currentTimeMillis();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            assertEquals(ROWS, rs.getInt(1));
        }

        return System.currentTimeMillis() - t0;
    }
}
