package de.hahn.blog.treenodeselect.view.beans;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

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

    public TreeSelectionBean() {
    }

    public void onSelection(ActionEvent actionEvent) {
        JUCtrlHierBinding treeBinding = null;
        // get the binding container
        BindingContainer bindings = BindingContext.getCurrent().getCurrentBindingsEntry();
        // get an ADF attributevalue from the ADF page definitions
        AttributeBinding attr = (AttributeBinding) bindings.getControlBinding("myLocation1");
        Integer node = (Integer) attr.getInputValue();

        _logger.info("Information: select node " + node);

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
        RowKeySet rks = searchTreeNode(root, node.toString());
        //define the row key set that determines the nodes to disclose.
        RowKeySet disclosedRowKeySet = buildDiscloseRowKeySet(treeBinding, rks);
        tree.setSelectedRowKeys(rks);
        tree.setDisclosedRowKeys(disclosedRowKeySet);
        //refresh the tree after the search
        AdfFacesContext.getCurrentInstance().addPartialTarget(getTree());
    }

    /**
     * Method that parses an ADF bound ADF Faces tree component to find
     * search string matches in one of the specified attribute names.
     * Attribute names are ignored if they don't exist in the search node.
     * The method performs a recursiv search and returns a RowKeySet with
     * the row keys of all nodes that contain the search string
     * @param node The JUCtrlHierNodeBinding instance to search
     * @param searchAttributes An array of attribute names to search in
     * @param searchType defines where the search is started within the
     * text. Valid values are
     * START, CONTAIN, END. If NULL the "CONTAIN" is set as the default
     * @param searchString The search condition
     * @return RowKeySet row keys
     */
    private RowKeySet searchTreeNode(JUCtrlHierNodeBinding node, String searchString) {
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

        Row nodeRow = node.getRow();
        if (nodeRow != null) {
            String compareString = "";
            try {
                Object attribute = nodeRow.getAttribute("LocationId");
                if (attribute instanceof String) {
                    compareString = (String) attribute;
                } else {
                    //try the toString method as a simple fallback
                    compareString = attribute.toString();
                }
                //not all nodes have all attributes. In this case an exception
                //is thrown that we don't need to handle as it is expected
            } catch (oracle.jbo.JboException attributeNotFound) {
                //node does not have attribute. Exclude from search
                _logger.log(Level.FINEST, "Attribute not found in node");
            }

            //compare strings case insensitive.
            if (compareString.toUpperCase().indexOf(searchString.toUpperCase()) > -1) {
                //get row key
                rowKeys.add(node.getKeyPath());
            }
        }

        List<JUCtrlHierNodeBinding> children = node.getChildren();
        if (children != null) {
            for (JUCtrlHierNodeBinding _node : children) {
                //Each child search returns a row key set that must be added to the
                //row key set returned by the overall search
                RowKeySet rks = searchTreeNode(_node, searchString);
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
            if (node != null && node.getParent() != null && !node.getParent()
                                                                 .getKeyPath()
                                                                 .isEmpty()) {
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
