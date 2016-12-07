package de.hahn.blog.treenodeselect.view.beans;

import java.util.Iterator;
import java.util.List;

import javax.faces.event.ActionEvent;

import oracle.adf.model.BindingContext;
import oracle.adf.share.logging.ADFLogger;
import oracle.adf.view.rich.component.rich.data.RichTree;
import oracle.adf.view.rich.context.AdfFacesContext;

import oracle.binding.AttributeBinding;
import oracle.binding.BindingContainer;

import oracle.jbo.Row;
import oracle.jbo.uicli.binding.JUCtrlHierBinding;
import oracle.jbo.uicli.binding.JUCtrlHierNodeBinding;

import org.apache.myfaces.trinidad.model.RowKeySet;
import org.apache.myfaces.trinidad.model.RowKeySetImpl;
import org.apache.myfaces.trinidad.model.TreeModel;
import org.apache.myfaces.trinidad.util.ComponentReference;


public class TreeSelectionBean {
    private static ADFLogger _logger = ADFLogger.createADFLogger(TreeSelectionBean.class);
    private ComponentReference _tree;
    private String nodeOnly = "true";

    public TreeSelectionBean() {
    }

    public void onSelection(ActionEvent actionEvent) {
        JUCtrlHierBinding treeBinding = null;
        // get the binding container
        BindingContainer bindings = BindingContext.getCurrent().getCurrentBindingsEntry();
        // get an ADF attributevalue from the ADF page definitions
        AttributeBinding attr = (AttributeBinding) bindings.getControlBinding("mySearchString1");
        String node = (String) attr.getInputValue();

        // nothing to search!
        // clear selected nodes
        if (node == null || node.isEmpty()){
            RichTree tree = getTree();
            RowKeySet rks = new RowKeySetImpl();
            tree.setDisclosedRowKeys(rks);
            //refresh the tree after the search
            AdfFacesContext.getCurrentInstance().addPartialTarget(getTree());

            return;
        }
        
        // get an ADF attributevalue from the ADF page definitions
        AttributeBinding attrNodeOnly = (AttributeBinding) bindings.getControlBinding("myNodeOnly1");
        String strNodeOnly = (String) attrNodeOnly.getInputValue();
        // if not initializued set it to false!
        if (strNodeOnly == null) {
            strNodeOnly = "false";
        }
        _logger.info("Information: search node only: " + strNodeOnly);

        //Get the JUCtrlHierbinding reference from the PageDef
        // For JDev 12c use the next two lines to get the treebinding
        TreeModel tmodel = (TreeModel) getTree().getValue();
        treeBinding = (JUCtrlHierBinding) tmodel.getWrappedData();
        // For JDev 11g use the next two lines to get the treebinding
        //        CollectionModel collectionModel = (CollectionModel)getTree().getValue();
        //        treeBinding = (JUCtrlHierBinding)collectionModel.getWrappedData();
        _logger.info("Information tree value:" + treeBinding);

        //Define a node to search in. In this example, the root node
        //is used
        JUCtrlHierNodeBinding root = treeBinding.getRootNodeBinding();
        //However, if the user used the "Show as Top" context menu option to
        //shorten the tree display, then we only search starting from this
        //top mode
        List topNode = (List) getTree().getFocusRowKey();
        if (topNode != null) {
            //make top node the root node for the search
            root = treeBinding.findNodeByKeyPath(topNode);
        }
        RichTree tree = getTree();
        RowKeySet rks = searchTreeNode(root, node.toString(), strNodeOnly);
        tree.setSelectedRowKeys(rks);
        //define the row key set that determines the nodes to disclose.
        RowKeySet disclosedRowKeySet = buildDiscloseRowKeySet(treeBinding, rks);
        tree.setDisclosedRowKeys(disclosedRowKeySet);
        //refresh the tree after the search
        AdfFacesContext.getCurrentInstance().addPartialTarget(tree);
    }

    /**
     * Method that parses an ADF bound ADF Faces tree component to find
     * search string matches in one of the specified attribute names.
     * Attribute names are ignored if they don't exist in the search node.
     * The method performs a recursiv search and returns a RowKeySet with
     * the row keys of all nodes that contain the search string
     * @param node The JUCtrlHierNodeBinding instance to search The start node to search
     * @param searchString The search condition
     * @param nodeOnly if true searches the visible node data only
     * @return RowKeySet row keys which contain hte search string
     */
    private RowKeySet searchTreeNode(JUCtrlHierNodeBinding node, String searchString, String nodeOnly) {
        RowKeySetImpl rowKeys = new RowKeySetImpl();
        //Sanity checks
        if (node == null) {
            _logger.info("Node passed as NULL");
            return rowKeys;
        }

        if (searchString == null || searchString.length() < 1) {
            _logger.info(node.getName() + ": Search string cannot be NULL or EMPTY");
            return rowKeys;
        }

        String compareString = "";


        if ("true".equals(nodeOnly)) {
            ///////////////////////////////////////////////////////////////////////
            // To search only the visible data in the tree node
            Object[] aValues = node.getAttributeValues();
            StringBuilder sb = new StringBuilder();
            for (Object aValue : aValues) {
                sb.append(aValue).append(" ");
            }
            _logger.info("node value:" + sb.toString());
            compareString = sb.toString();

            //compare strings case insensitive.
            if (compareString.toUpperCase().indexOf(searchString.toUpperCase()) > -1) {
                //get row key
                rowKeys.add(node.getKeyPath());
            }
        } else {
            //////////////////////////////////////////////////////////////////////
            // to search the whole row used to build the tree node
            Row nodeRow = node.getRow();
            if (nodeRow != null) {
                Object[] attributeValues = nodeRow.getAttributeValues();
                for (Object obj : attributeValues) {
                    if (obj != null) {
                        compareString = obj.toString();
                    }

                    //compare strings case insensitive.
                    if (compareString.toUpperCase().indexOf(searchString.toUpperCase()) > -1) {
                        //get row key
                        rowKeys.add(node.getKeyPath());
                    }
                }
            }
        }
        List<JUCtrlHierNodeBinding> children = node.getChildren();
        if (children != null) {
            for (JUCtrlHierNodeBinding _node : children) {
                //Each child search returns a row key set that must be added to the
                //row key set returned by the overall search
                RowKeySet rks = searchTreeNode(_node, searchString, nodeOnly);
                if (rks != null && rks.size() > 0) {
                    rowKeys.addAll(rks);
                }
            }
        }
        return rowKeys;
    }

    /**
     * Helper method that returns a list of parent node for the RowKeySet
     * passed as the keys argument. The RowKeySet can be used to disclose
     * the folders in which the keys reside. Node that to disclose a full
     * branch, all RowKeySet that are in the path must be defined
     *
     * @param treeBinding ADF tree binding instance read from the PageDef
     * file
     * @param keys RowKeySet containing List entries of oracle.jbo.Key
     * @return RowKeySet of parent keys to disclose
     */
    private RowKeySet buildDiscloseRowKeySet(JUCtrlHierBinding treeBinding, RowKeySet keys) {
        RowKeySetImpl discloseRowKeySet = new RowKeySetImpl();
        Iterator iter = keys.iterator();
        while (iter.hasNext()) {
            List keyPath = (List) iter.next();
            JUCtrlHierNodeBinding node = treeBinding.findNodeByKeyPath(keyPath);
            if (node != null && node.getParent() != null && !node.getParent().getKeyPath().isEmpty()) {
                //store the parent path
                discloseRowKeySet.add(node.getParent().getKeyPath());
                //call method recursively until no parents are found
                RowKeySetImpl parentKeySet = new RowKeySetImpl();
                parentKeySet.add(node.getParent().getKeyPath());
                RowKeySet rks = buildDiscloseRowKeySet(treeBinding, parentKeySet);
                discloseRowKeySet.addAll(rks);
            }
        }
        return discloseRowKeySet;
    }

    public void setTree(RichTree tree) {


        _tree = ComponentReference.newUIComponentReference(tree);
    }

    public RichTree getTree() {
        if (_tree != null) {
            return (RichTree) (_tree.getComponent());
        }
        return null;
    }
}
