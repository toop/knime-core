/*
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   Jul 5, 2006 (wiswedel): created
 */
package de.unikn.knime.core.node;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import de.unikn.knime.core.data.DataTable;
import de.unikn.knime.core.data.DataTableSpec;
import de.unikn.knime.core.data.RowIterator;
import de.unikn.knime.core.data.container.ContainerTable;
import de.unikn.knime.core.data.container.DataContainer;
import de.unikn.knime.core.data.container.RearrangeColumnsTable;
import de.unikn.knime.core.data.container.TableSpecReplacerTable;
import de.unikn.knime.core.util.FileUtil;

/**
 * DataTable implementation that is passed along the KNIME workflow. This 
 * implementation is provided in a NodeModel's 
 * {@link de.unikn.knime.core.node.NodeModel#execute(
 * BufferedDataTable[], ExecutionContext) execute} method as input data and
 * must also be returned as output data. 
 * 
 * <p><code>BufferedDataTable</code> are not created directly (via a 
 * constructor, for instance) but they are rather instantiated using the 
 * <code>ExecutionContext</code> that is provided in the execute method. See
 * its {@link de.unikn.knime.core.node.ExecutionContext class description} for
 * more details on how to create <code>BufferedDataTable</code>. 
 * 
 * @author wiswedel, University of Konstanz
 */
public final class BufferedDataTable implements DataTable {
    
    private static final NodeLogger LOGGER = 
        NodeLogger.getLogger(BufferedDataTable.class);
  
    /** Contains the repository of DataTables that are available whilst some
     * workflow is being loaded. Most of the times, this map is empty!
     */
    private static final HashMap<Integer, HashMap<Integer, BufferedDataTable>>
        LOADER_HASH =  
            new HashMap<Integer, HashMap<Integer, BufferedDataTable>>();
    
    /** Inits a repository, do not use this method in a node implementation.
     * This method is called right before a workflow is loaded. Nodes will
     * add handles to their DataTables to a global repository while they are
     * loading and once the loading process is finished, the repository is
     * cleaned up.
     * @param loadID An ID generated by the WorkflowManager to make this
     * loading process unique.
     */
    public static void initRepository(final int loadID) {
        LOGGER.debug("Adding new table repository for id " + loadID + ", ("
                + LOADER_HASH.size() + " in total)");
        LOADER_HASH.put(loadID, new HashMap<Integer, BufferedDataTable>());
    }
    
    /**
     * Clears a table repository, only for internal use! This method is called
     * when the loading process has been completed. It's not intended to be
     * called from a node implementation!
     * @param loadID The ID to clear.
     */
    public static void clearRepository(final int loadID) {
        Object removed = LOADER_HASH.remove(loadID);
        if (removed == null) {
            LOGGER.warn("No table repository for id " + loadID);
        } else {
            LOGGER.debug("Removed table repository for id " + loadID + ", ("
                    + LOADER_HASH.size() + " left)");
        }
    }
    
    /**
     * Method that is used internally while the workflow is being loaded. Not 
     * intended to be used directly by node implementations.  
     * @param loadID The loading ID
     * @param tableID The table ID
     * @return The table from the repository.
     * @throws InvalidSettingsException If no such table exists.
     */
    public static BufferedDataTable getDataTable(final int loadID, 
            final Integer tableID) throws InvalidSettingsException {
        HashMap<Integer, BufferedDataTable> hash = LOADER_HASH.get(loadID);
        if (hash == null) {
            throw new InvalidSettingsException("No BufferedDataTable " 
                    + "repository for load ID " + loadID);
        }
        BufferedDataTable result = hash.get(tableID);
        if (result == null) {
            throw new InvalidSettingsException("No BufferedDataTable " 
                    + " with ID " + tableID);
        }
        // update the lastID counter!
        assert result.m_tableID == tableID;
        lastID = Math.max(tableID, lastID);
        return result;
    }
    
    /**
     * Method that is used internally while the workflow is being loaded. Not 
     * intended to be used directly by node implementations.  
     * @param loadID The loading ID
     * @param t The table to put into the repository.
     * @throws InvalidSettingsException If no loading ID exists.
     */
    public static void putDataTable(final int loadID, 
            final BufferedDataTable t) throws InvalidSettingsException {
        HashMap<Integer, BufferedDataTable> hash = LOADER_HASH.get(loadID);
        if (hash == null) {
            throw new InvalidSettingsException("No BufferedDataTable " 
                    + "repository for load ID " + loadID);
        }
        hash.put(t.getBufferedTableId(), t);
    }
    
    /** internal ID for any generated table. */
    private static int lastID = 0;
    private final KnowsRowCountTable m_delegate;
    private int m_tableID;
    private Node m_owner;
    
    /** Creates a new buffered data table based on a container table 
     * (caching everything).
     * @param table The reference.
     */ 
    BufferedDataTable(final ContainerTable table) {
        this((KnowsRowCountTable)table);
    }
    
    /** Creates a new buffered data table based on a changed columns table
     * (only memorize rows that changed).
     * @param table The reference.
     */ 
    BufferedDataTable(final RearrangeColumnsTable table) {
        this((KnowsRowCountTable)table);
    }
    
    /** Creates a new buffered data table based on a changed spec table 
     * (only keep new spec).
     * @param table The reference.
     */ 
    BufferedDataTable(final TableSpecReplacerTable table) {
        this((KnowsRowCountTable)table);
    }
    
    private BufferedDataTable(final KnowsRowCountTable table) {
        m_delegate = table;
        m_tableID = lastID++;
    }

    /** Factory method. Obsolete, not used anymore.
     * @param table The table to cache
     * @param exec For cancellation/progress
     * @return A new buffered datatable
     * @throws CanceledExecutionException If canceled.
     */
    static BufferedDataTable createBufferedDataTable(
            final DataTable table, final ExecutionMonitor exec)
            throws CanceledExecutionException {
        boolean isKnown = false;
        if (table instanceof ContainerTable) {
            isKnown = true;
        } else if (table instanceof RearrangeColumnsTable) {
            isKnown = true;
        } else if (table instanceof TableSpecReplacerTable) {
            isKnown = true;
        } else if (table instanceof BufferedDataTable) {
            LOGGER.coding("No need to create a BufferedDataTable based " 
                    + "on a BufferedDataTable; you can use the object at hand "
                    + "directly.");
            return (BufferedDataTable)table;
        }
        if (isKnown) {
            LOGGER.coding("Attempted to create a BufferedDataTable "
                    + "with a known table implemenation (" 
                    + table.getClass().getSimpleName() + "), keeping only " 
                    + "reference. Use one of the constructors in the future!");
            return new BufferedDataTable((KnowsRowCountTable)table);
        } 
        ContainerTable t = (ContainerTable)DataContainer.cache(table, exec);
        return new BufferedDataTable(t);
    }
    
    /** Get reference to reference table, if any. This returns a non-null
     * value if this table represents for instance a changed column table.
     * For the container table, the reference is <code>null</code>.
     * @return The reference table, if any.
     */
    BufferedDataTable getReferenceTable() {
        if (m_delegate instanceof RearrangeColumnsTable) {
            return ((RearrangeColumnsTable)m_delegate).getReferenceTable();
        } else if (m_delegate instanceof TableSpecReplacerTable) {
            return ((TableSpecReplacerTable)m_delegate).getReferenceTable();
        } 
        return null;
    }
    
    /**
     * @see de.unikn.knime.core.data.DataTable#getDataTableSpec()
     */
    public DataTableSpec getDataTableSpec() {
        return m_delegate.getDataTableSpec();
    }

    /**
     * @see de.unikn.knime.core.data.DataTable#iterator()
     */
    public RowIterator iterator() {
        return m_delegate.iterator();
    }

    /**
     * Get the row count of the this table.
     * @return Number of rows in the table.  
     */
    public int getRowCount() {
        return m_delegate.getRowCount();
    }
    
    /** Method being used internally, not interesting for the implementor of
     * a new node model. It will return some unique ID to identify the table
     * while loading.
     * @return The unique ID.
     */
    public Integer getBufferedTableId() {
        return m_tableID;
    }
    
    private static final String CFG_TABLE_META = "table_meta_info";
    private static final String CFG_TABLE_REFERENCE = "table_reference";
    private static final String CFG_TABLE_TYPE = "table_type";
    private static final String CFG_TABLE_ID = "table_ID";
    private static final String CFG_TABLE_FILE_NAME = "table_file_name";
    private static final String TABLE_TYPE_CONTAINER = "container_table";
    private static final String TABLE_TYPE_REARRANGE_COLUMN = 
        "rearrange_columns_table";
    private static final String TABLE_TYPE_NEW_SPEC = "new_spec_table";
    private static final String TABLE_SUB_DIR = "reference";
    private static final String TABLE_FILE = "data.zip";
    
    /** Saves the table to a directory and writes some settings to the argument
     * NodeSettingsWO object. It will also write the reference table in case
     * this node is responsible for it (i.e. this node created the reference
     * table).
     * @param dir The directory to write to.
     * @param settings The settings to store few meta information.
     * @param exec The progress monitor for cancellation.
     * @throws IOException If writing fails.
     * @throws CanceledExecutionException If canceled.
     */
    void save(final File dir, final NodeSettingsWO settings, 
            final ExecutionMonitor exec) 
        throws IOException, CanceledExecutionException {
        NodeSettingsWO s = settings.addNodeSettings(CFG_TABLE_META);
        s.addInt(CFG_TABLE_ID, getBufferedTableId());
        File outFile = new File(dir, TABLE_FILE);
        s.addString(CFG_TABLE_FILE_NAME, TABLE_FILE);
        m_delegate.saveToFile(outFile, s, exec);
        if (m_delegate instanceof ContainerTable) {
            s.addString(CFG_TABLE_TYPE, TABLE_TYPE_CONTAINER);
        } else { 
            if (m_delegate instanceof RearrangeColumnsTable) {
                s.addString(CFG_TABLE_TYPE, TABLE_TYPE_REARRANGE_COLUMN);
            } else {
                assert m_delegate instanceof TableSpecReplacerTable;
                s.addString(CFG_TABLE_TYPE, TABLE_TYPE_NEW_SPEC);
            }
            BufferedDataTable reference = getReferenceTable();
            assert reference != null;
            if (reference.getOwner() == getOwner()) {
                File subDir = new File(dir, TABLE_SUB_DIR);
                subDir.mkdir();
                if (!subDir.exists() || !subDir.canWrite()) {
                    throw new IOException("Unable to write directory "
                            + subDir.getAbsolutePath());
                }
                s.addString(CFG_TABLE_REFERENCE, TABLE_SUB_DIR);
                reference.save(subDir, s, exec);
            } else {
                s.addString(CFG_TABLE_REFERENCE, null);
            }
        }
    }
    
    /** Factory method to restore a table that has been written using
     * the save method.
     * @param dir The directory to load from.
     * @param settings The settings to load from.
     * @param exec The exec mon for progress/cancel
     * @param loadID The public loading ID
     * @return The table as written by save.
     * @throws IOException If reading fails.
     * @throws CanceledExecutionException If canceled.
     * @throws InvalidSettingsException If settings are invalid.
     */
    static BufferedDataTable loadFromFile(final File dir,
            final NodeSettingsRO settings, final ExecutionMonitor exec,
            final int loadID) throws IOException, CanceledExecutionException,
            InvalidSettingsException {
        HashMap<Integer, BufferedDataTable> hash =
            LOADER_HASH.get(loadID);
        if (hash == null) {
            throw new IOException(
                    "There is no table repository with ID " + loadID + "\n"
                    + "(Valid are " 
                    + Arrays.toString(LOADER_HASH.keySet().toArray())
                    + ")");
        }
        NodeSettingsRO s = settings.getNodeSettings(CFG_TABLE_META);
        int id = s.getInt(CFG_TABLE_ID);
        String fileName = s.getString(CFG_TABLE_FILE_NAME);
        File file = new File(dir, fileName);
        File dest = DataContainer.createTempFile();
        dest.deleteOnExit();
        FileUtil.copy(file, dest, exec);
        String tableType = s.getString(CFG_TABLE_TYPE);
        BufferedDataTable t;
        if (tableType.equals(TABLE_TYPE_CONTAINER)) {
            ContainerTable fromContainer = 
                (ContainerTable)DataContainer.readFromZip(dest); 
            t = new BufferedDataTable(fromContainer);
        } else if (tableType.equals(TABLE_TYPE_REARRANGE_COLUMN)
                || (tableType.equals(TABLE_TYPE_NEW_SPEC))) {
            String reference = s.getString(CFG_TABLE_REFERENCE, null);
            if (reference != null) {
                File referenceDir = new File(dir, reference);
                loadFromFile(referenceDir, s, exec, loadID);
            }
            if (tableType.equals(TABLE_TYPE_REARRANGE_COLUMN)) {
                t = new BufferedDataTable(
                        new RearrangeColumnsTable(dest, s, loadID));
            } else {
                t = new BufferedDataTable(
                        new TableSpecReplacerTable(dest, s, loadID));
            }
        } else {
            throw new InvalidSettingsException("Unknown table identifier: "
                    + tableType);
        }
        t.m_tableID = id;
        lastID = Math.max(id, lastID);
        hash.put(id, t);
        return t;
    }
    
    /**
     * @return Returns the owner.
     */
    Node getOwner() {
        return m_owner;
    }

    /**
     * @param owner The owner to set.
     */
    void setOwnerRecursively(final Node owner) {
        if (m_owner == null) {
            m_owner = owner;
            BufferedDataTable reference = getReferenceTable();
            if (reference != null) {
                reference.setOwnerRecursively(owner);
            }
        }
    }

    /** Internally used interface. You won't have any benefit by implementing
     * this interface! It's used for selected classes in the KNIME core.
     */
    public static interface KnowsRowCountTable extends DataTable {
        /** Row count of the table. 
         * @return The row count.
         */
        int getRowCount();
        
        /** Save the table to a file.
         * @param f To write to.
         * @param settings To add meta information to.
         * @param exec For progress/cancel.
         * @throws IOException If writing fails.
         * @throws CanceledExecutionException If canceled.
         */
        void saveToFile(final File f, final NodeSettingsWO settings, 
                final ExecutionMonitor exec) 
                throws IOException, CanceledExecutionException;
    }
}



