/*
 * Copyright (C) 2012 Pavel Stastny
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
package cz.incad.kramerius.processes.impl;

import static cz.incad.kramerius.processes.database
        .ProcessDatabaseUtils
        .registerProcess;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import cz.incad.kramerius.utils.conf.KConfiguration;
import org.apache.commons.io.FileUtils;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import cz.incad.kramerius.processes.BatchStates;
import cz.incad.kramerius.processes.DefinitionManager;
import cz.incad.kramerius.processes.LRProcess;
import cz.incad.kramerius.processes.LRProcessDefinition;
import cz.incad.kramerius.processes.LRProcessManager;
import cz.incad.kramerius.processes.LRProcessOrdering;
import cz.incad.kramerius.processes.NotReadyException;
import cz.incad.kramerius.processes.ProcessManagerException;
import cz.incad.kramerius.processes.States;
import cz.incad.kramerius.processes.database.ProcessDatabaseUtils;
import cz.incad.kramerius.security.User;
import cz.incad.kramerius.security.database.TypeOfOrdering;
import cz.incad.kramerius.users.LoggedUsersSingleton;
import cz.incad.kramerius.utils.database.JDBCCommand;
import cz.incad.kramerius.utils.database.JDBCQueryTemplate;
import cz.incad.kramerius.utils.database.JDBCTransactionTemplate;
import cz.incad.kramerius.utils.database.JDBCUpdateTemplate;
import cz.incad.kramerius.utils.database.Offset;
import cz.incad.kramerius.utils.database.SQLFilter;
import cz.incad.kramerius.utils.properties.PropertiesStoreUtils;

public class DatabaseProcessManager implements LRProcessManager {


    public static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(DatabaseProcessManager.class.getName());


    @Inject
    @Named("kramerius4")
    private Provider<Connection> connectionProvider;

    @Inject
    private DefinitionManager lrpdm;

    @Inject
    private Provider<User> userProvider;

//    @Inject
//    private LoggedUsersSingleton loggedUsersSingleton;

    private final Lock lock = new ReentrantLock();

    public DatabaseProcessManager() {
        super();
    }

    @Override
    public Connection getConnection() {
        Connection connection = connectionProvider.get();
        if (connection == null) {
            throw new NotReadyException("connection not ready");
        }
        try {
            connection.setTransactionIsolation(KConfiguration.getInstance().getConfiguration().getInt("jdbcProcessTransactionIsolationLevel", Connection.TRANSACTION_READ_COMMITTED));
        } catch (SQLException e) {
            throw new NotReadyException("connection not ready - " + e);
        }
        return connection;
    }

    @Override
    public LRProcess getLongRunningProcess(String uuid) {
        lock.lock();
        try (Connection connection = getConnection()) {
            String sql = "select " + ProcessDatabaseUtils.printQueryProcessColumns() + " from PROCESSES p where UUID = ?";
            List<LRProcess> processes = new JDBCQueryTemplate<LRProcess>(connection) {
                @Override
                public boolean handleRow(ResultSet rs, List<LRProcess> returnsList) throws SQLException {
                    LRProcess lrProcess = processFromResultSet(rs);
                    if (lrProcess != null) {
                        returnsList.add(lrProcess);
                        return super.handleRow(rs, returnsList);
                    } else {
                        return true; //continue with other rows after ignoring this one
                    }
                }
            }.executeQuery(sql, uuid);
            return !processes.isEmpty() ? processes.get(0) : null;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }


    @Override
    public Integer registerLongRunningProcess(LRProcess lp, String loggedUserKey, Properties parametersMapping) {
        lock.lock();
        try (Connection connection = getConnection()) {

            if (lp.getUser() == null) {
                return registerProcess(connection, lp, PropertiesStoreUtils.storeProperties(parametersMapping));
            } else {
                return registerProcess(connection, lp, /* this.userProvider.get() */lp.getUser(), lp.getLoggedUserKey(), PropertiesStoreUtils.storeProperties(parametersMapping));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public void updateLongRunningProcessPID(LRProcess lrProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            int pid = Integer.parseInt(lrProcess.getPid());
            new JDBCUpdateTemplate(connection).executeUpdate("update processes set PID = ? where UUID = ?", pid, lrProcess.getUUID());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateLongRunningProcessName(LRProcess lrProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            new JDBCUpdateTemplate(connection).executeUpdate("update processes set NAME = ? where UUID = ?", shortenIfTooLong(lrProcess.getProcessName(), 512), lrProcess.getUUID());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private String shortenIfTooLong(String string, int maxLength) {
        if (string == null || string.isEmpty() || string.length() <= maxLength) {
            return string;
        } else {
            String suffix = "...";
            return string.substring(0, maxLength - suffix.length()) + suffix;
        }
    }

    @Override
    public void deleteBatchLongRunningProcess(LRProcess longRunningProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            if (longRunningProcess.getProcessState().equals(States.RUNNING)) {
                throw new ProcessManagerException("cannot delete process with state '" + longRunningProcess.getProcessState() + "'");
            }

            List<JDBCCommand> commands = new ArrayList<JDBCCommand>();

            final String token = longRunningProcess.getGroupToken();
            final List<LRProcess> childSubprecesses = getLongRunningProcessesByGroupToken(token);


            final int id = ProcessDatabaseUtils.getProcessId(longRunningProcess, connection);
            commands.add(new JDBCCommand() {

                @Override
                public Object executeJDBCCommand(Connection con) throws SQLException {
                    PreparedStatement prepareStatement = con.prepareStatement("delete from PROCESS_2_TOKEN where process_id = ?");
                    prepareStatement.setInt(1, id);
                    return prepareStatement.executeUpdate();
                }
            });

            for (final LRProcess lrProcess : childSubprecesses) {
                if (lrProcess.getAuthToken() != null) {
                    commands.add(new JDBCCommand() {

                        @Override
                        public Object executeJDBCCommand(Connection con) throws SQLException {
                            PreparedStatement prepareStatement = con.prepareStatement("delete from PROCESS_2_TOKEN where auth_token = ?");
                            prepareStatement.setString(1, lrProcess.getAuthToken());
                            return prepareStatement.executeUpdate();
                        }
                    });

                }
            }

            commands.add(new JDBCCommand() {

                @Override
                public Object executeJDBCCommand(Connection con) throws SQLException {
                    PreparedStatement prepareStatement = con.prepareStatement("delete from PROCESS_2_TOKEN where auth_token = ?");
                    prepareStatement.setString(1, token);
                    return prepareStatement.executeUpdate();
                }
            });


            commands.add(new JDBCCommand() {

                @Override
                public Object executeJDBCCommand(Connection con) throws SQLException {
                    PreparedStatement prepareStatement = con.prepareStatement("delete from PROCESSES where token = ?");
                    prepareStatement.setString(1, token);
                    return prepareStatement.executeUpdate();
                }
            });

            JDBCTransactionTemplate.Callbacks callbacks = new JDBCTransactionTemplate.Callbacks() {

                @Override
                public void rollbacked() {
                    // do nothing
                }

                @Override
                public void commited() {
                    for (int i = 0; i < childSubprecesses.size(); i++) {
                        LRProcess child = childSubprecesses.get(i);
                        File chWDir = child.processWorkingDirectory();
                        try {
                            FileUtils.deleteDirectory(chWDir);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, e.getMessage(), e);
                        }
                    }
                }
            };

            new JDBCTransactionTemplate(connection, true).updateWithTransaction(callbacks, (JDBCCommand[]) commands.toArray(new JDBCCommand[commands.size()]));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new ProcessManagerException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteLongRunningProcess(final LRProcess lrProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            if (lrProcess.getProcessState().equals(States.RUNNING)) {
                throw new ProcessManagerException("cannot delete process with state '" + lrProcess.getProcessState() + "'");
            }

            final int id = ProcessDatabaseUtils.getProcessId(lrProcess, connection);
            final String uuid = lrProcess.getUUID();
            JDBCCommand deleteTokensMapping = new JDBCCommand() {

                @Override
                public Object executeJDBCCommand(Connection con) throws SQLException {
                    PreparedStatement prepareStatement = con.prepareStatement("delete from PROCESS_2_TOKEN where process_id = ?");
                    prepareStatement.setInt(1, id);
                    return prepareStatement.executeUpdate();
                }
            };

            JDBCCommand deleteProcess = new JDBCCommand() {

                @Override
                public Object executeJDBCCommand(Connection con) throws SQLException {
                    PreparedStatement prepareStatement = con.prepareStatement("delete from processes where UUID = ?");
                    prepareStatement.setString(1, uuid);
                    return prepareStatement.executeUpdate();
                }
            };
            JDBCTransactionTemplate.Callbacks callbacks = new JDBCTransactionTemplate.Callbacks() {

                @Override
                public void rollbacked() {
                    // do nothing
                }

                @Override
                public void commited() {
                    try {
                        File processWorkingDirectory = lrProcess.processWorkingDirectory();
                        FileUtils.deleteDirectory(processWorkingDirectory);
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            };
            new JDBCTransactionTemplate(connection, true).updateWithTransaction(callbacks, deleteTokensMapping, deleteProcess);

            // TODO: zruseni associace uzivatel - session -> aby se nehromadili
            // hornici
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new ProcessManagerException(e);
        } finally {
            lock.unlock();
        }

    }

    public void updateLongRunningProcessStartedDate(LRProcess lrProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            new JDBCUpdateTemplate(connection).executeUpdate("update processes set STARTED = ? where UUID = ?", new Timestamp(lrProcess.getStartTime()), lrProcess.getUUID());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }


    public void updateLongRunningProcessFinishedDate(LRProcess lrProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            new JDBCUpdateTemplate(connection).executeUpdate("update processes set FINISHED = ? where UUID = ?", new Timestamp(lrProcess.getFinishedTime()), lrProcess.getUUID());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void updateLongRunningProcessState(LRProcess lrProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            int val = lrProcess.getProcessState().getVal();
            String processUuid = lrProcess.getUUID();
            LOGGER.fine("params is " + val + "," + processUuid);
            new JDBCUpdateTemplate(connection).executeUpdate("update processes set STATUS = ? where UUID = ?", val, processUuid);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void updateLongRunninngProcessBatchState(LRProcess lrProcess) {
        lock.lock();
        try (Connection connection = getConnection()) {
            int val = lrProcess.getBatchState().getVal();
            String processUuid = lrProcess.getUUID();
            LOGGER.fine("params is " + val + "," + processUuid);
            new JDBCUpdateTemplate(connection).executeUpdate("update processes set BATCH_STATUS = ? where UUID = ?", val, processUuid);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<LRProcess> getPlannedProcess(int howMany) {
        lock.lock();
        try (Connection connection = getConnection()) {
            StringBuffer buffer = new StringBuffer("select " + ProcessDatabaseUtils.printQueryProcessColumns() + " from processes p where status = ? ORDER BY PLANNED LIMIT ? ");
            List<LRProcess> processes = new JDBCQueryTemplate<LRProcess>(connection) {
                @Override
                public boolean handleRow(ResultSet rs, List<LRProcess> returnsList) throws SQLException {
                    LRProcess processFromResultSet = processFromResultSet(rs);
                    if (processFromResultSet != null) {
                        returnsList.add(processFromResultSet);
                        return super.handleRow(rs, returnsList);
                    } else {
                        return true; //continue with other rows after ignoring this one
                    }
                }
            }.executeQuery(buffer.toString(), States.PLANNED.getVal(), howMany);
            return processes;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return Collections.EMPTY_LIST;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getNumberOfLongRunningProcesses(SQLFilter filter) {
        try (Connection connection = getConnection()) {
            StringBuilder builder = new StringBuilder("select count(*) from processes p "
                    + " join process_grouped_view v on (p.process_id=v.process_id) ");

            if (filter != null) {
                builder.append(filter.getSQLOffset());
            }

            List<Integer> countList = new JDBCQueryTemplate<Integer>(connection) {
                @Override
                public boolean handleRow(ResultSet rs, List<Integer> returnsList) throws SQLException {
                    returnsList.add(rs.getInt(1));
                    return super.handleRow(rs, returnsList);
                }
            }.executeQuery(builder.toString(), filter != null ? filter.getObjectsToPreparedStm().toArray() : new Object[]{});

            return !countList.isEmpty() ? countList.get(0) : 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return 0;
        } finally {
            lock.unlock();
        }
    }


    private LRProcess processFromResultSet(ResultSet rs) throws SQLException {
        // CREATE TABLE PROCESSES(DEFID VARCHAR, UUID VARCHAR ,PID
        // VARCHAR,STARTED timestamp, STATUS int
        String definitionId = rs.getString("DEFID");
        String pid = rs.getString("PID");
        String uuid = rs.getString("UUID");
        int status = rs.getInt("STATUS");
        Timestamp planned = rs.getTimestamp("PLANNED");
        Timestamp started = rs.getTimestamp("STARTED");
        Timestamp finished = rs.getTimestamp("FINISHED");
        String name = rs.getString("PNAME");
        String params = rs.getString("PARAMS");
        String token = rs.getString("TOKEN");
        String authToken = rs.getString("AUTH_TOKEN");
        int startedBy = rs.getInt("STARTEDBY");
        String loginname = rs.getString("LOGINNAME");
        String firstname = rs.getString("FIRSTNAME");
        String surname = rs.getString("SURNAME");
        String userKey = rs.getString("USER_KEY");
        String paramsMapping = rs.getString("params_mapping");
        int batchStatus = rs.getInt("BATCH_STATUS");
        String ipAddr = rs.getString("IP_ADDR");


        LRProcessDefinition definition = this.lrpdm.getLongRunningProcessDefinition(definitionId);
        if (definition == null) {
            LOGGER.fine("cannot find definition '" + definitionId + "'");
            return null;
        }

        LRProcess process = definition.loadProcess(uuid, pid, planned != null ? planned.getTime() : 0, States.load(status), BatchStates.load(batchStatus), name);

        process.setGroupToken(token);
        process.setAuthToken(authToken);

        if (started != null)
            process.setStartTime(started.getTime());
        if (params != null) {
            String[] paramsArray = params.split(",");
            process.setParameters(Arrays.asList(paramsArray));
        }

        process.setFirstname(firstname);
        process.setSurname(surname);
        process.setLoginname(loginname);
        process.setLoggedUserKey(userKey);

        if (paramsMapping != null) {
            Properties props = PropertiesStoreUtils.loadProperties(paramsMapping);
            process.setParametersMapping(props);
        }

        if (finished != null) {
            process.setFinishedTime(finished.getTime());
        }
        if (ipAddr != null) {
            process.setPlannedIPAddress(ipAddr);
        }

        return process;
    }

    @Override
    public List<LRProcess> getLongRunningProcessesByGroupToken(String token) {
        lock.lock();
        try (Connection connection = getConnection()) {
            List<LRProcess> lpList = new JDBCQueryTemplate<LRProcess>(connection) {
                @Override
                public boolean handleRow(ResultSet rs, List<LRProcess> returnsList) throws SQLException {
                    LRProcess process = processFromResultSet(rs);
                    if (process != null) {
                        returnsList.add(process);
                        return true;
                    } else {
                        return true; //continue with other rows after ignoring this one
                    }
                }
            }.executeQuery("select " + ProcessDatabaseUtils.printQueryProcessColumns() + " from processes p where token = ? " + " order by p.process_id ", token);
            return lpList;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return Collections.EMPTY_LIST;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<LRProcess> getLongRunningProcesses(States state) {
        lock.lock();
        try (Connection connection = getConnection()) {
            List<LRProcess> processes = new JDBCQueryTemplate<LRProcess>(connection) {
                @Override
                public boolean handleRow(ResultSet rs, List<LRProcess> returnsList) throws SQLException {
                    LRProcess lrProcess = processFromResultSet(rs);
                    if (lrProcess != null) {
                        returnsList.add(lrProcess);
                        return super.handleRow(rs, returnsList);
                    } else {
                        return true; //continue with other rows after ignoring this one
                    }
                }
            }.executeQuery("select " + ProcessDatabaseUtils.printQueryProcessColumns() + " from PROCESSES p  where STATUS = ?",
                    state.getVal());
            return processes;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return Collections.EMPTY_LIST;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<LRProcess> getLongRunningProcessesAsFlat(LRProcessOrdering ordering, TypeOfOrdering typeOfOrdering, Offset offset) {
        lock.lock();
        try (Connection connection = getConnection()) {
            StringBuffer buffer = new StringBuffer("select " + ProcessDatabaseUtils.printQueryProcessColumns() + " from processes p ");
            if (ordering != null) {
                buffer.append(" order by ");
                ordering(ordering, typeOfOrdering, buffer);
                if (ordering != LRProcessOrdering.PLANNED) {
                    buffer.append(',');
                    ordering(LRProcessOrdering.PLANNED, typeOfOrdering, buffer);
                }
            }
            if (offset != null) {
                buffer.append(offset.getSQLOffset());
            }
            List<LRProcess> processes = new JDBCQueryTemplate<LRProcess>(connection) {
                @Override
                public boolean handleRow(ResultSet rs, List<LRProcess> returnsList) throws SQLException {
                    LRProcess lrProcess = processFromResultSet(rs);
                    if (lrProcess != null) {
                        returnsList.add(lrProcess);
                        return super.handleRow(rs, returnsList);
                    } else {
                        return true; //continue with other rows after ignoring this one
                    }
                }
            }.executeQuery(buffer.toString());
            return processes;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return Collections.EMPTY_LIST;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<LRProcess> getLongRunningProcessesAsGrouped(LRProcessOrdering ordering, TypeOfOrdering typeOfOrdering, Offset offset, SQLFilter filter) {
        lock.lock();
        try (Connection connection = getConnection()) {
            StringBuffer buffer = new StringBuffer("select " + ProcessDatabaseUtils.printQueryProcessColumns() + ",v.pcount as pcount from processes p "
                    + " join process_grouped_view v on (p.process_id=v.process_id)");

            if (filter != null) {
                buffer.append(filter.getSQLOffset());
            }

            if (ordering != null) {
                buffer.append(" order by ");
                ordering(ordering, typeOfOrdering, buffer);
                if (ordering != LRProcessOrdering.PLANNED) {
                    buffer.append(',');
                    ordering(LRProcessOrdering.PLANNED, typeOfOrdering, buffer);
                }
            }

            if (offset != null) {
                buffer.append(offset.getSQLOffset());
            }

            List<LRProcess> processes = new JDBCQueryTemplate<LRProcess>(connection) {
                @Override
                public boolean handleRow(ResultSet rs, List<LRProcess> returnsList) throws SQLException {
                    LRProcess lrProcess = processFromResultSet(rs);
                    if (lrProcess != null) {
                        returnsList.add(lrProcess);
                        int processCount = rs.getInt("pcount");
                        lrProcess.setMasterProcess(processCount > 1);
                        return super.handleRow(rs, returnsList);
                    } else {
                        return true; //continue with other rows after ignoring this one
                    }
                }
            }.executeQuery(buffer.toString(), filter != null ? filter.getObjectsToPreparedStm().toArray() : new Object[]{});

            return processes;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return Collections.EMPTY_LIST;
        } finally {
            lock.unlock();
        }
    }

    private void ordering(LRProcessOrdering ordering, TypeOfOrdering typeOfOrdering, StringBuffer buffer) {
        if (ordering != null) {
            buffer.append(ordering.getOrdering()).append(' ');
        }

        if (typeOfOrdering != null) {
            buffer.append(typeOfOrdering.getTypeOfOrdering()).append(' ');

            if (typeOfOrdering == TypeOfOrdering.ASC) {
                buffer.append("NULLS FIRST").append(' ');
            } else {
                buffer.append("NULLS LAST").append(' ');
            }
        }
    }

    @Override
    public boolean isSessionKeyAssociatedWithProcess(String sessionKey) {
        lock.lock();
        try (Connection connection = getConnection()) {
            List<String> list = ProcessDatabaseUtils.getAssociatedTokens(sessionKey, connection);
            return !list.isEmpty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void closeAuthToken(String authToken) {
        lock.lock();
        try (Connection connection = getConnection()) {
            ProcessDatabaseUtils.updateTokenActive(connection, authToken, false);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }


    @Override
    public boolean isAuthTokenClosed(String authToken) {
        lock.lock();
        try (Connection connection = getConnection()) {
            List<Boolean> flags = new JDBCQueryTemplate<Boolean>(connection) {

                @Override
                public boolean handleRow(ResultSet rs, List<Boolean> returnsList) throws SQLException {
                    returnsList.add(rs.getBoolean("token_active"));
                    return super.handleRow(rs, returnsList);
                }
            }.executeQuery("select token_active from processes where auth_token=?", authToken);

            // no flags
            if (flags.isEmpty()) return true;

            for (Boolean flag : flags) {
                if (!flag.booleanValue()) return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return true;
        } finally {
            lock.unlock();
        }
    }


    @Override
    public Properties loadParametersMapping(LRProcess lrProcess) {
        Properties properties = new Properties();
        lock.lock();
        try (Connection connection = getConnection()){
            List<String> params = new JDBCQueryTemplate<String>(connection, true) {
                @Override
                public boolean handleRow(ResultSet rs, List<String> returnsList) throws SQLException {
                    String paramsMapping = rs.getString("params_mapping");
                    returnsList.add(paramsMapping);
                    return super.handleRow(rs, returnsList);
                }

            }.executeQuery("select params_mapping from processes where uuid = ?", lrProcess.getUUID());

            if (params.isEmpty()) {
                properties.load(new StringReader(params.get(0)));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
        return properties;

    }


    @Override
    public String getSessionKey(String authToken) {
        lock.lock();
        try (Connection connection = getConnection()){
            List<String> list = ProcessDatabaseUtils.getAssociatedSessionKeys(authToken, connection);
            return !list.isEmpty() ? list.get(0) : null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateAuthTokenMapping(LRProcess lrProcess, String sessionKey) {
//        lock.lock();
//        try (Connection connection = getConnection()){
//            
//            int sessionKeyId = this.loggedUsersSingleton.getSessionKeyId(sessionKey);
//            if (sessionKeyId > -1) {
//                ProcessDatabaseUtils.updateAuthTokenMapping(lrProcess, connection, sessionKeyId);
//            } else {
//                throw new ProcessManagerException("cannot find session associated with sessionKey '" + sessionKey + "'");
//            }
//        } catch (SQLException e) {
//            LOGGER.log(Level.SEVERE, e.getMessage(), e);
//        } finally {
//            lock.unlock();
//        }
    }

    @Override
    public List<LRProcess> getLongRunningProcesses() {
        return getLongRunningProcessesAsGrouped(null, null, null, null);
    }

    @Override
    public Lock getSynchronizingLock() {
        return this.lock;
    }

}
