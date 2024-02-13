/*
 * Copyright (C) 2010 Pavel Stastny
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.incad.kramerius.security.database;

import static cz.incad.kramerius.database.cond.ConditionsInterpretHelper.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.antlr.stringtemplate.StringTemplate;

import cz.incad.kramerius.database.VersionService;
import cz.incad.kramerius.users.database.LoggedUserDbHelper;
import cz.incad.kramerius.utils.DatabaseUtils;
import cz.incad.kramerius.utils.IOUtils;
import cz.incad.kramerius.utils.database.JDBCCommand;
import cz.incad.kramerius.utils.database.JDBCQueryTemplate;
import cz.incad.kramerius.utils.database.JDBCTransactionTemplate;
import cz.incad.kramerius.utils.database.JDBCUpdateTemplate;

public class SecurityDbInitializer {

    static Logger LOGGER = Logger.getLogger(SecurityDbInitializer.class.getName());

    public static void initDatabase(Connection connection, VersionService versionService) {
        try {
            if (versionService.getVersion() == null) {

                if (!DatabaseUtils.tableExists(connection, "USER_ENTITY")) {
                    createSecurityTables(connection);
                }
                // column DEACTIVATED has been created
                makeSureThatUserEntity_DEACTIVATED(connection);

                //TODO: Move method
                LoggedUserDbHelper.createLoggedUsersTablesIfNotExists(connection);

                // the profiles table has been created + insert public role
                makeSureThatProfilesTable(connection);

                // public role created
                makeSurePublicRoleInserted(connection);

                // create public role
                insertRightForDisplayAdminMenu(connection);

                // insert right for virtual collection manage
                insertRightForVirtualCollection(connection);

                // insert right for criteria params manage
                insertRightForCriteriaParamsManage(connection);

                // k4 replication rights
                insertRightK4ReplicationExport(connection);
                insertRightK4ReplicationImport(connection);

                // mets ndk import
                insertNDKMetsImport(connection);
                // replikator k3
                insertReplikatorK3(connection);

                // insert aggregate process right
                insertAggregateRight(connection);

                insertShowStatiticsRight(connection);

                //sort right
                insertSortRight(connection);
                insertPrintRight(connection);
                updateUserEntityTable(connection);
                updateShowItems(connection);

                // labels table
                makeSureThatLabelsTable(connection);
                makeSureThatRoleColumnExists(connection);
                createNewActions(connection);

            } else {
                String v = versionService.getVersion();

                if (versionCondition(v, ">=", "4.5.0") && versionCondition(v, "<=", "4.8.0")) {

                    makeSureThatUserEntity_DEACTIVATED(connection);

                    //TODO: Move method
                    LoggedUserDbHelper.createLoggedUsersTablesIfNotExists(connection);
                    // the profiles table has been created
                    makeSureThatProfilesTable(connection);
                    // create public role
                    makeSurePublicRoleInserted(connection);

                    // create public role
                    insertRightForDisplayAdminMenu(connection);
                    // right for virtual collection
                    insertRightForVirtualCollection(connection);

                    // right for criteria params manage
                    insertRightForCriteriaParamsManage(connection);

                    // k4 replication rights
                    insertRightK4ReplicationExport(connection);
                    insertRightK4ReplicationImport(connection);

                    // mets ndk import
                    insertNDKMetsImport(connection);
                    // replikator k3
                    insertReplikatorK3(connection);

                    // insert aggregate process right
                    insertAggregateRight(connection);
                    // insert statistics right
                    insertShowStatiticsRight(connection);

                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);

                    createNewActions(connection);

                } else if (versionCondition(v, "=", "5.3.0")) {
                    // right for criteria params manage
                    insertRightForCriteriaParamsManage(connection);
                    // k4 replication rights
                    insertRightK4ReplicationExport(connection);
                    insertRightK4ReplicationImport(connection);

                    // mets ndk import
                    insertNDKMetsImport(connection);
                    // replikator k3
                    insertReplikatorK3(connection);

                    // insert aggregate process right
                    insertAggregateRight(connection);
                    // insert statistics right
                    insertShowStatiticsRight(connection);

                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);


                } else if (versionCondition(v, "=", "5.4.0")) {
                    // k4 replication rights
                    insertRightK4ReplicationExport(connection);
                    insertRightK4ReplicationImport(connection);

                    // mets ndk import
                    insertNDKMetsImport(connection);
                    // replikator k3
                    insertReplikatorK3(connection);
                    // insert aggregate process right
                    insertAggregateRight(connection);
                    // insert statistics right
                    insertShowStatiticsRight(connection);

                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);


                } else if (versionCondition(v, "=", "5.5.0")) {
                    // mets ndk import
                    insertNDKMetsImport(connection);
                    // replikator k3
                    insertReplikatorK3(connection);
                    // insert aggregate process right
                    insertAggregateRight(connection);
                    // insert statistics right
                    insertShowStatiticsRight(connection);
                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);

                } else if (versionCondition(v, "=", "5.6.0")) {
                    // replikator k3
                    insertReplikatorK3(connection);
                    // insert aggregate process right
                    insertAggregateRight(connection);
                    // insert statistics right
                    insertShowStatiticsRight(connection);
                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);

                } else if (versionCondition(v, "=", "5.7.0")) {
                    // insert aggregate process right
                    insertAggregateRight(connection);
                    // insert statistics right
                    insertShowStatiticsRight(connection);
                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);

                } else if (versionCondition(v, "=", "5.8.0")) {
                    // insert aggregate process right
                    insertAggregateRight(connection);
                    // insert statistics right
                    insertShowStatiticsRight(connection);
                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);
                    makeSureThatRoleColumnExists(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);

                } else if (versionCondition(v, "=", "5.9.0")) {
                    // insert statistics right
                    insertShowStatiticsRight(connection);
                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);


                } else if ((versionCondition(v, ">", "5.9.0")) && (versionCondition(v, "<", "6.3.0"))) {
                    //sort right
                    insertSortRight(connection);
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    
                    createNewActions(connection);

                } else if (versionCondition(v, "=", "6.3.0")) {
                    insertPrintRight(connection);
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    
                    createNewActions(connection);


                } else if (versionCondition(v, "=", "6.6.0")) {
                    updateUserEntityTable(connection);
                    updateShowItems(connection);

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);

                    createNewActions(connection);


                } else if (versionCondition(v, ">", "6.6.0") && versionCondition(v, "<=", "6.6.2")) {
                    updateShowItems(connection);
                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);


                } else if (versionCondition(v, ">", "6.6.2") && versionCondition(v, "<", "7.0.2")) {

                    // labels table
                    makeSureThatLabelsTable(connection);
                    makeSureThatRoleColumnExists(connection);
                    createNewActions(connection);

                }

            }

            // checks role column in right entity
            makeSureThatRoleColumnExists(connection);
            // update role column from relations
            makeSureRolesInTable(connection);

            // labels table
            makeSureThatLabelsTable(connection);

            updateExistingActions(connection);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    }


    /**
     * @param connection
     * @return
     * @throws SQLException
     */
    private static int insertAggregateRight(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_aggregate").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    /**
     * @param connection
     * @return
     * @throws SQLException
     */
    private static int insertShowStatiticsRight(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_showstatistics").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }


    private static int insertSortRight(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_sort").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static int insertPrintRight(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_show_print_menu").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static int updateUserEntityTable(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("updateUserEntities").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        template.setUseReturningKeys(false);
        return template.executeUpdate(sql);
    }

    private static int updateShowItems(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("updateShowItems").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        template.setUseReturningKeys(false);
        return template.executeUpdate(sql);
    }

    /**
     * @param connection
     * @return
     * @throws SQLException
     */
    private static int insertReplikatorK3(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_ReplikatorK3").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static int insertRightK4ReplicationExport(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_K4ReplicationExport").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static int insertRightK4ReplicationImport(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_K4ReplicationImport").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static int insertRightForCriteriaParamsManage(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_DisplayAdminMenu").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static int insertNDKMetsImport(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_NDKMetsImport").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static void makeSureThatProfilesTable(Connection connection) throws SQLException, IOException {
        if (!DatabaseUtils.tableExists(connection, "PROFILES")) {
            // create tables for public users - 4.5.0 - version
            createPublicUsersAndProfilesTables(connection); // Zavislost na active users
        }
    }

    private static void makeSureThatLabelsTable(Connection connection) throws SQLException, IOException {
        if (!DatabaseUtils.tableExists(connection, "LABELS_ENTITY")) {
            createLabelsTable(connection);
        }
    }

    private static void makeSurePublicRoleInserted(Connection conn) throws SQLException {
        List<Integer> result = new JDBCQueryTemplate<Integer>(conn, false) {

            @Override
            public boolean handleRow(ResultSet rs, List<Integer> returnsList) throws SQLException {
                returnsList.add(rs.getInt("group_id"));
                return true;
            }

        }.executeQuery("select * from group_entity where gname = ?", "public_users");

        if (result.isEmpty()) {
            // create public role
            insertPublicRole(conn);
        }
    }

    private static void makeSureThatUserEntity_DEACTIVATED(Connection connection) throws SQLException {
        // nesmi byt v transakci ... ?
        if (!DatabaseUtils.columnExists(connection, "USER_ENTITY", "DEACTIVATED")) {

            new JDBCTransactionTemplate(connection, false).updateWithTransaction(
                    new JDBCCommand() {

                        @Override
                        public Object executeJDBCCommand(Connection con) throws SQLException {
                            alterSecurityTableActiveColumn(con);
                            return null;
                        }
                    },
                    new JDBCCommand() {

                        @Override
                        public Object executeJDBCCommand(Connection con) throws SQLException {
                            updateSecurityTableActiveColumn(con);
                            return null;
                        }
                    }
            );
        }
    }

    private static int insertParams(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertParams_SecuredStreams").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    private static int insertPublicRole(Connection connection) throws SQLException {
        StringTemplate stemplate = SecurityDatabaseUtils.stGroup().getInstanceOf("insertPublicRole");
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(stemplate.toString());
    }

    private static int insertRightForDisplayAdminMenu(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertDisplayAdminMenuRight").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }

    public static int insertRightForVirtualCollection(Connection connection) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_VirtualCollections").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql);
    }


    private static int insertCriterium(Connection connection, int paramid) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertCriterium_SecuredStreams").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql, paramid);
    }

    private static int insertRight(Connection connection, int groupId, int criteriumid) throws SQLException {
        String sql = SecurityDatabaseUtils.stUdateRightGroup().getInstanceOf("insertRight_SecuredStreams").toString();
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        return template.executeUpdate(sql, criteriumid, groupId);
    }

    private static void createSecurityTables(Connection connection) throws SQLException, IOException {
        InputStream is = InitSecurityDatabaseMethodInterceptor.class.getResourceAsStream("res/initsecdb.sql");
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        template.setUseReturningKeys(false);
        template.executeUpdate(IOUtils.readAsString(is, Charset.forName("UTF-8"), true));
    }

    public static void createPublicUsersAndProfilesTables(Connection connection) throws SQLException, IOException {
        InputStream is = SecurityDbInitializer.class.getResourceAsStream("res/initpublicusers.sql");
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        template.setUseReturningKeys(false);
        String sqlScript = IOUtils.readAsString(is, Charset.forName("UTF-8"), true);
        template.executeUpdate(sqlScript);
    }

    public static void createLabelsTable(Connection connection) throws SQLException, IOException {
        InputStream is = InitSecurityDatabaseMethodInterceptor.class.getResourceAsStream("res/initlabels.sql");
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        template.setUseReturningKeys(false);
        template.executeUpdate(IOUtils.readAsString(is, Charset.forName("UTF-8"), true));
    }

    public static void alterSecurityTableActiveColumn(Connection con) throws SQLException {
        PreparedStatement prepareStatement = con.prepareStatement("ALTER TABLE USER_ENTITY ADD COLUMN DEACTIVATED BOOLEAN");
        prepareStatement.executeUpdate();
        LOGGER.log(Level.FINEST, "ALTER TABLE: updated rows");
    }

    public static void updateSecurityTableActiveColumn(Connection con) throws SQLException {
        PreparedStatement prepareStatement = con.prepareStatement("UPDATE USER_ENTITY set DEACTIVATED = FALSE");
        int r = prepareStatement.executeUpdate();
        LOGGER.log(Level.FINEST, "UPDATE TABLE: updated rows {0}", r);
    }



    public static void alterTableAddRoleColumn(Connection con) throws SQLException {
        PreparedStatement prepareStatement = con.prepareStatement("ALTER TABLE right_entity ADD COLUMN ROLE TEXT");
        prepareStatement.executeUpdate();
        LOGGER.log(Level.FINEST, "ALTER TABLE: updated rows");
    }

    private static void updateExistingActions(Connection connection) throws SQLException, IOException {
        InputStream is = InitSecurityDatabaseMethodInterceptor.class.getResourceAsStream("res/rename_oldactions.sql");
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        template.setUseReturningKeys(false);
        template.executeUpdate(IOUtils.readAsString(is, Charset.forName("UTF-8"), true));
    }

    /** Premena na nove akce */
    // vytvareni 
    private static void createNewActions(Connection connection) throws SQLException, IOException {
        InputStream is = InitSecurityDatabaseMethodInterceptor.class.getResourceAsStream("res/initsecdb_newactions.sql");
        JDBCUpdateTemplate template = new JDBCUpdateTemplate(connection, false);
        template.setUseReturningKeys(false);
        template.executeUpdate(IOUtils.readAsString(is, Charset.forName("UTF-8"), true));
    }

    
    public static void updateRolesFromRelations(Connection con) throws SQLException {
        String update = "UPDATE right_entity " +
                "SET \"role\"=subquery.gname " +
                "FROM (SELECT group_id, gname " +
                "      FROM  group_entity) AS subquery " +
                "WHERE (right_entity.group_id=subquery.group_id) and (right_entity.role is null) and (right_entity.group_id is not null)";
        PreparedStatement prepareStatement = con.prepareStatement(update);
        int i = prepareStatement.executeUpdate();
        LOGGER.log(Level.FINEST, "UPDATE TABLE: updated rows "+i);

    }

    private static void makeSureThatRoleColumnExists(Connection connection) throws SQLException {
        if (!DatabaseUtils.columnExists(connection, "right_entity", "role")) {
            new JDBCTransactionTemplate(connection, false).updateWithTransaction(
                    new JDBCCommand() {

                        @Override
                        public Object executeJDBCCommand(Connection con) throws SQLException {
                            alterTableAddRoleColumn(con);
                            return null;
                        }
                    }
            );
        }
    }

    public static void makeSureRolesInTable(Connection connection) throws SQLException {
        new JDBCTransactionTemplate(connection, false).updateWithTransaction(
                new JDBCCommand() {

                    @Override
                    public Object executeJDBCCommand(Connection con) throws SQLException {
                        updateRolesFromRelations(con);
                        return null;
                    }
                }
        );
    }
}
