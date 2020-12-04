package ru.neoflex.emf.sparksql;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.spark.sql.catalyst.analysis.NamedRelation;
import org.apache.spark.sql.catalyst.analysis.UnresolvedFunction;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.BinaryOperator;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.SubqueryAlias;
import org.apache.spark.sql.catalyst.trees.TreeNode;
import org.apache.spark.sql.execution.SparkSqlParser;
import org.apache.spark.sql.internal.SQLConf;
import org.eclipse.emf.ecore.resource.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.restserver.DBServerSvc;
import ru.neoflex.emf.restserver.JsonHelper;
import scala.collection.Iterator;

import javax.annotation.PostConstruct;

@RestController()
@RequestMapping("/sparksql")
public class SparksqlController {
    final DBServerSvc dbServerSvc;

    public SparksqlController(DBServerSvc dbServerSvc) {
        this.dbServerSvc = dbServerSvc;
    }

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(SparksqlPackage.eINSTANCE);
    }

    Node createNode(TreeNode treeNode) {
        Node result;
        if (treeNode instanceof Project) {
            result = createProjectNode((Project) treeNode);
        } else if (treeNode instanceof Attribute) {
            result = createAttributeNode((Attribute) treeNode);
        } else if (treeNode instanceof Literal) {
            result = createLiteralNode((Literal) treeNode);
        } else if (treeNode instanceof BinaryOperator) {
            result = createBinaryOperatorNode((BinaryOperator) treeNode);
        } else if (treeNode instanceof SubqueryAlias) {
            result = createSubqueryAliasNode((SubqueryAlias) treeNode);
        } else if (treeNode instanceof NamedRelation) {
            result = createNamedRelationNode((NamedRelation) treeNode);
        } else if (treeNode instanceof Filter) {
            result = createFilterNode((Filter) treeNode);
        } else if (treeNode instanceof UnresolvedFunction) {
            result = createUnresolvedFunctionNode((UnresolvedFunction) treeNode);
        } else {
            result = SparksqlFactory.eINSTANCE.createNode();
        }
        result.setNodeName(treeNode.getClass().getSimpleName());
        result.setDescription(treeNode.toString());
        result.setLine(treeNode.origin().line().getOrElse(() -> 0) - 1);
        result.setStartPosition(treeNode.origin().startPosition().getOrElse(() -> null));
        Iterator it = treeNode.children().iterator();
        while (it.hasNext()) {
            TreeNode child = (TreeNode) it.next();
            result.getChildren().add(createNode(child));
        }
        return result;
    }

    private Node createUnresolvedFunctionNode(UnresolvedFunction treeNode) {
        UnresolvedFunctionNode result = SparksqlFactory.eINSTANCE.createUnresolvedFunctionNode();
        result.setName(treeNode.name().toString());
        for (Iterator it = treeNode.arguments().iterator(); it.hasNext(); ) {
            TreeNode t = (TreeNode) it.next();
            result.getArguments().add(createNode(t));
        }
        return result;
    }

    private Node createProjectNode(Project treeNode) {
        ProjectNode result = SparksqlFactory.eINSTANCE.createProjectNode();
        for (Iterator it = treeNode.projectList().iterator(); it.hasNext(); ) {
            TreeNode t = (TreeNode) it.next();
            result.getProjectList().add(createNode(t));
        }
        return result;
    }

    private Node createAttributeNode(Attribute treeNode) {
        AttributeNode result = SparksqlFactory.eINSTANCE.createAttributeNode();
        result.setName(treeNode.name());
        return result;
    }

    private Node createLiteralNode(Literal treeNode) {
        LiteralNode result = SparksqlFactory.eINSTANCE.createLiteralNode();
        result.setValue(treeNode.value());
        result.setDataType(treeNode.dataType().typeName());
        return result;
    }

    private Node createBinaryOperatorNode(BinaryOperator treeNode) {
        BinaryOperatorNode result = SparksqlFactory.eINSTANCE.createBinaryOperatorNode();
        result.setSymbol(treeNode.symbol());
        return result;
    }

    private Node createSubqueryAliasNode(SubqueryAlias treeNode) {
        SubqueryAliasNode result = SparksqlFactory.eINSTANCE.createSubqueryAliasNode();
        result.setAlias(treeNode.alias());
        return result;
    }

    private Node createNamedRelationNode(NamedRelation treeNode) {
        NamedRelationNode result = SparksqlFactory.eINSTANCE.createNamedRelationNode();
        result.setName(treeNode.name());
        return result;
    }

    private Node createFilterNode(Filter treeNode) {
        FilterNode result = SparksqlFactory.eINSTANCE.createFilterNode();
        result.setCondition(createNode(treeNode.condition()));
        return result;
    }

    @PostMapping(value = "/parse", consumes = {"text/plain"})
    public JsonNode parse(@RequestBody String sql) throws Exception {
        SQLConf sqlConf = new SQLConf();
        SparkSqlParser parser = new SparkSqlParser(sqlConf);
        LogicalPlan plan = parser.parsePlan(sql);
        QueryLogicalPlan queryLogicalPlan = SparksqlFactory.eINSTANCE.createQueryLogicalPlan();
        queryLogicalPlan.setSql(sql);
        Node planNode = createNode(plan);
        queryLogicalPlan.setLogicalPlan(planNode);
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            Resource resource = tx.createResource();
            resource.getContents().add(queryLogicalPlan);
            resource.save(null);
            return JsonHelper.resourceToJson(resource);
        });
    }

}
