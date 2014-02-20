/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by 
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME. The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 *
 * History
 *   Jun 12, 2012 (wiswedel): created
 */
package org.knime.core.node.streamable;


/** A merge operator combines {@link StreamableOperatorInternals} that are
 * created by different threads (or JVM processes) to one internals object
 * that is then passed to the {@link org.knime.core.node.NodeModel#
 * finishStreamableExecution(StreamableOperatorInternals,
 * org.knime.core.node.ExecutionContext, PortOutput[])} method.
 *
 * <p>A merge is required for instance when a model is generated by node but
 * the data is processed in a distributed way (e.g. Naive Bayes processes
 * the data in a distributed way and the merge operator then aggregates the
 * statistics calculated on each of the data partitions). A merge operator
 * can also be used to collect information from different partitions (e.g.
 * to create an warning message or collect summary information for a view).
 *
 * @since 2.6
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
public abstract class MergeOperator {

    /**
     * Whether the merge can be done hierarchically. If not hierarchical all
     * operator internals are passed to one call of the
     * {@link #mergeFinal(StreamableOperatorInternals[])} method.
     *
     * @return whether merge can be done hierarchically. The default
     * implementation returns <code>false</code>.
     */
    public boolean isHierarchical() {
        return false;
    }

    /**
     * Performs the merge and outputs one new internals object that contains all
     * information that can be passed to {@link org.knime.core.node.NodeModel#
     * iterate(StreamableOperatorInternals)} (or to another merge if
     * hierarchical merges are possible).
     *
     * @param operators The operators to merge, never null.
     * @return The aggregated internals in a new object.
     */
    public StreamableOperatorInternals mergeIntermediate(
            final StreamableOperatorInternals[] operators) {
        throw new IllegalStateException("Not implemented");
    }

    /**
     * Performs the merge and outputs one new internals object that contains all
     * information that can be passed to the NodeModel's finish execution method
     * (or to another merge if hierarchical merges are possible).
     *
     * @param operators The operators to merge, never null.
     * @return The aggregated internals in a new object.
     */
    public abstract StreamableOperatorInternals mergeFinal(
            final StreamableOperatorInternals[] operators);

}
