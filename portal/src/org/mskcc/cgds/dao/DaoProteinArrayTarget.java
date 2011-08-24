
package org.mskcc.cgds.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author jj
 */
public class DaoProteinArrayTarget {
    // use a MySQLbulkLoader instead of SQL "INSERT" statements to load data into table
    private static MySQLbulkLoader myMySQLbulkLoader = null;
    private static DaoProteinArrayTarget daoProteinArrayTarget;

    /**
     * Private Constructor to enforce Singleton Pattern.
     */
    private DaoProteinArrayTarget() {
    }

    /**
     * Gets Global Singleton Instance.
     *
     * @return DaoProteinArrayTarget Singleton.
     * @throws DaoException Database Error.
     */
    public static DaoProteinArrayTarget getInstance() throws DaoException {
        if (daoProteinArrayTarget == null) {
            daoProteinArrayTarget = new DaoProteinArrayTarget();
        }

        if (myMySQLbulkLoader == null) {
            myMySQLbulkLoader = new MySQLbulkLoader("protein_array_target");
        }
        return daoProteinArrayTarget;
    }

    /**
     * Adds a new ProteinArrayTarget Record to the Database.
     *
     * @return number of records successfully added.
     * @throws DaoException Database Error.
     */
    public int addProteinArrayTarget(String proteinArrayId, long entrezGeneId) throws DaoException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            if (MySQLbulkLoader.isBulkLoad()) {
                //  write to the temp file maintained by the MySQLbulkLoader
                myMySQLbulkLoader.insertRecord(proteinArrayId,
                        Long.toString(entrezGeneId));
                // return 1 because normal insert will return 1 if no error occurs
                return 1;
            } else {
                con = JdbcUtil.getDbConnection();
                pstmt = con.prepareStatement
                        ("INSERT INTO protein_array_target (`PROTEIN_ARRAY_ID`,`ENTREZ_GENE_ID`) "
                                + "VALUES (?,?)");
                pstmt.setString(1, proteinArrayId);
                pstmt.setLong(2, entrezGeneId);
                int rows = pstmt.executeUpdate();
                return rows;
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(con, pstmt, rs);
        }
    }

    /**
     * Loads the temp file maintained by the MySQLbulkLoader into the DMBS.
     *
     * @return number of records inserted
     * @throws DaoException Database Error.
     */
    public int flushProteinArrayTargetsToDatabase() throws DaoException {
        try {
            return myMySQLbulkLoader.loadDataFromTempFileIntoDBMS();
        } catch (IOException e) {
            System.err.println("Could not open temp file");
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Gets the list of protein array target with the Specified entrez gene ID.
     *
     * @param entrezId entrez gene ID.
     * @return ProteinArrayInfo Object.
     * @throws DaoException Database Error.
     */
    public Collection<String> getProteinArrayIds(long entrezId) throws DaoException {
        return getProteinArrayIds(Collections.singleton(entrezId));
    }

    /**
     * Gets the list of protein array target with the Specified entrez gene ID.
     *
     * @param entrezIds entrez gene ID.
     * @return ProteinArrayInfo Object.
     * @throws DaoException Database Error.
     */
    public Collection<String> getProteinArrayIds(Collection<Long> entrezIds) throws DaoException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection();
            pstmt = con.prepareStatement
                    ("SELECT PROTEIN_ARRAY_ID FROM protein_array_target WHERE ENTREZ_GENE_ID in ("
                    +StringUtils.join(entrezIds,",")+")");
            
            Collection<String> set = new HashSet<String>();
            rs = pstmt.executeQuery();
            while (rs.next()) {
                set.add(rs.getString(1));
            }
            
            return set;
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(con, pstmt, rs);
        }
    }

    /**
     * Deletes all protein array target Records in the Database.
     *
     * @throws DaoException Database Error.
     */
    public void deleteAllRecords() throws DaoException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection();
            pstmt = con.prepareStatement("TRUNCATE TABLE protein_array_target");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(con, pstmt, rs);
        }
    }
}